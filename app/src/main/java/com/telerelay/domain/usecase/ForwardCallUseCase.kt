package com.telerelay.domain.usecase

import com.telerelay.domain.logic.CallStateMachine
import com.telerelay.domain.model.CallNotification
import com.telerelay.domain.model.CallState
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.domain.model.OutgoingMessage
import com.telerelay.domain.port.CallerNumberResolver
import com.telerelay.domain.port.Clock
import com.telerelay.domain.port.ContactNameResolver
import com.telerelay.domain.port.MessageFormatter
import com.telerelay.domain.port.MessageSender
import com.telerelay.domain.port.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Call pipeline: interpret state transitions → resolve number (API 31+:
 * RINGING broadcast → call log) → resolve contact name → format → deliver.
 * Singleton because it owns the long-lived state machine.
 *
 * The state machine is touched from two concurrent entry points (the telephony
 * callback and the PHONE_STATE broadcast receiver), so every machine access is
 * confined to a single thread — the 750 ms grace window deliberately happens
 * OUTSIDE that critical section so late broadcast numbers can still land.
 * The SIM marker is intentionally omitted on the call path: the public API
 * carries no subscription on ringing events, and a wrong guess is worse than
 * no marker.
 */
@Singleton
class ForwardCallUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val formatter: MessageFormatter,
    private val sender: MessageSender,
    private val resolver: CallerNumberResolver,
    private val contacts: ContactNameResolver,
    private val clock: Clock,
) {

    private val machine = CallStateMachine(clock)
    private val machineDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** Feeds the number captured by [com.telerelay.receiver.PhoneStateReceiver]. */
    suspend fun onIncomingNumber(state: CallState?, number: String?, atMillis: Long) =
        withContext(machineDispatcher) {
            machine.onIncomingNumber(state, number, atMillis)
        }

    suspend fun onEvent(event: CallStateEvent) {
        val notification = withContext(machineDispatcher) { machine.onEvent(event) } ?: return
        val s = settings.current()
        if (!s.isConfigured) return

        when (notification) {
            is CallNotification.Incoming -> {
                if (!s.callNotificationEnabled) return
                val number = event.number
                    ?: withContext(machineDispatcher) { machine.peekBroadcastNumber(event.atMillis) }
                    ?: awaitBroadcastNumber()
                    ?: resolver.resolve(sinceMillis = event.atMillis)
                sender.sendOrEnqueue(OutgoingMessage(formatter.incomingCall(displayName(number))))
            }

            is CallNotification.Missed -> {
                if (!s.missedCallNotificationEnabled) return
                val ringingAt = withContext(machineDispatcher) { machine.lastRingingAtMillis }
                val number = notification.number ?: resolver.resolve(sinceMillis = ringingAt)
                sender.sendOrEnqueue(OutgoingMessage(formatter.missedCall(displayName(number))))
            }
        }
    }

    /**
     * The number-bearing RINGING broadcast usually trails the telephony
     * callback by a few hundred milliseconds; wait one short beat for it
     * before giving up on the caller name. Non-consuming (peek): the number
     * must survive for the missed notification of the same call.
     */
    private suspend fun awaitBroadcastNumber(): String? {
        delay(BROADCAST_GRACE_MILLIS)
        return withContext(machineDispatcher) { machine.peekBroadcastNumber(clock.nowMillis()) }
    }

    /** Contact name when saved, the raw number otherwise (null stays null → "Bilinmiyor"). */
    private fun displayName(number: String?): String? =
        number?.let { contacts.resolve(it) ?: it }

    /** Called when monitoring restarts so a stale ring from the previous session is forgotten. */
    suspend fun onMonitoringRestarted() = withContext(machineDispatcher) { machine.reset() }

    companion object {
        private const val BROADCAST_GRACE_MILLIS = 750L
    }
}
