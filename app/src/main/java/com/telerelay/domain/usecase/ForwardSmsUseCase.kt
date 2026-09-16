package com.telerelay.domain.usecase

import com.telerelay.di.ApplicationScope
import com.telerelay.domain.logic.MultipartSmsAssembler
import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.FilterDecision
import com.telerelay.domain.model.IncomingSms
import com.telerelay.domain.port.Clock
import com.telerelay.domain.port.MessageFormatter
import com.telerelay.domain.port.MessageSender
import com.telerelay.domain.port.PrivacyFilter
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.domain.port.SimInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS pipeline: assemble multipart segments → privacy guard → format → deliver.
 * Runs once per SMS broadcast; cheap early exits keep the common case fast.
 */
class ForwardSmsUseCase @Inject constructor(
    private val assembler: MultipartSmsAssembler,
    private val settings: SettingsRepository,
    private val filter: PrivacyFilter,
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
        val decision = if (s.privacyGuardEnabled) {
            filter.evaluate(sms.sender, sms.body)
        } else {
            FilterDecision.Allow
        }

        val body = when (decision) {
            FilterDecision.Allow -> sms.body
            FilterDecision.Exclude -> return
            is FilterDecision.Masked -> decision.maskedBody
        }

        val text = formatter.sms(
            sender = sms.sender,
            body = body,
            receivedAtMillis = sms.receivedAtMillis,
            sim = simInfo.activeSim(),
        )
        sender.sendOrEnqueue(text)
    }

    companion object {
        /** Small buffer so the flush timer never fires microseconds too early. */
        private const val FLUSH_GRACE_MILLIS = 250L
    }
}
