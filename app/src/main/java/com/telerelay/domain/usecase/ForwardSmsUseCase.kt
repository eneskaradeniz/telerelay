package com.telerelay.domain.usecase

import com.telerelay.di.ApplicationScope
import com.telerelay.domain.logic.AdSmsDetector
import com.telerelay.domain.logic.MultipartSmsAssembler
import com.telerelay.domain.logic.OtpCodeDetector
import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.IncomingSms
import com.telerelay.domain.model.OutgoingMessage
import com.telerelay.domain.port.Clock
import com.telerelay.domain.port.ContactNameResolver
import com.telerelay.domain.port.MessageFormatter
import com.telerelay.domain.port.MessageSender
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.domain.port.SimInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS pipeline: assemble multipart segments → ad filter → format → deliver.
 * Runs once per SMS broadcast; cheap early exits keep the common case fast.
 */
class ForwardSmsUseCase @Inject constructor(
    private val assembler: MultipartSmsAssembler,
    private val settings: SettingsRepository,
    private val contacts: ContactNameResolver,
    private val formatter: MessageFormatter,
    private val sender: MessageSender,
    private val simInfo: SimInfoProvider,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {

    suspend operator fun invoke(incoming: IncomingSms) {
        val current = settings.current()
        if (!current.smsForwardingEnabled || !current.isConfigured) return

        // Opportunistic flush: buffers whose quiet window already elapsed while
        // no timer was running (e.g. app process was cold-started by this SMS).
        assembler.emitExpired().forEach { deliver(it, current) }

        val completed = assembler.offer(incoming)
        if (completed != null) {
            deliver(completed, current)
        } else {
            scheduleWindowFlush()
        }
    }

    private fun scheduleWindowFlush() {
        scope.launch {
            delay(assembler.quietWindowMillis + FLUSH_GRACE_MILLIS)
            val latest = settings.current()
            if (latest.smsForwardingEnabled && latest.isConfigured) {
                assembler.emitExpired().forEach { deliver(it, latest) }
            }
        }
    }

    private suspend fun deliver(sms: IncomingSms, s: AppSettings) {
        val contactName = contacts.resolve(sms.sender)
        val code = OtpCodeDetector.find(sms.body)
        // Runs on the assembled body: opt-out wording sits at the end of long
        // multipart ads. Codes and saved contacts always get through — a
        // missed OTP costs far more than one stray ad.
        if (s.adFilterEnabled && code == null && contactName == null && AdSmsDetector.isAd(sms.body)) {
            return
        }
        val text = formatter.sms(
            // Resolve fails for non-contacts (short codes like "2273", service
            // alphanumerics like "E-DEVLET") — fall back to the raw sender.
            // "Bilinmiyor" is reserved for calls, where the number can be
            // genuinely unknown; an SMS always has a sender.
            sender = contactName ?: sms.sender,
            body = sms.body,
            sim = simInfo.simMarker(sms.subscriptionId),
        )
        sender.sendOrEnqueue(OutgoingMessage(text = text, copyText = code))
    }

    companion object {
        /** Small buffer so the flush timer never fires microseconds too early. */
        private const val FLUSH_GRACE_MILLIS = 250L
    }
}
