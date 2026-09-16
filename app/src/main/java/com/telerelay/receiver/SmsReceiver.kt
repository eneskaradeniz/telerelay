package com.telerelay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.telerelay.di.ApplicationScope
import com.telerelay.domain.model.IncomingSms
import com.telerelay.domain.port.Clock
import com.telerelay.domain.usecase.ForwardSmsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives incoming SMS broadcasts and hands each one to [ForwardSmsUseCase].
 *
 * Thin by contract: parse PDUs, delegate, return — no business logic here.
 * The receiver instance is recreated for every broadcast, so any cross-broadcast
 * state (multipart buffers) lives behind the singleton graph.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var forwardSms: ForwardSmsUseCase
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstNotNullOfOrNull { it.originatingAddress } ?: return
        // Segments delivered within one broadcast are joined; segments arriving
        // as separate broadcasts are merged later by the multipart assembler.
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val sms = IncomingSms(
            sender = sender,
            body = body,
            receivedAtMillis = clock.nowMillis(),
            segmentCount = messages.size,
        )
        scope.launch { forwardSms(sms) }
    }
}
