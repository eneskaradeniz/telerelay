package com.telerelay.domain.usecase

import com.telerelay.domain.logic.CallStateMachine
import com.telerelay.domain.model.CallNotification
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.domain.port.CallerNumberResolver
import com.telerelay.domain.port.Clock
import com.telerelay.domain.port.MessageFormatter
import com.telerelay.domain.port.MessageSender
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.domain.port.SimInfoProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Call pipeline: interpret state transitions → resolve number (API 31+) →
 * format → deliver. Singleton because it owns the long-lived state machine.
 */
@Singleton
class ForwardCallUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val formatter: MessageFormatter,
    private val sender: MessageSender,
    private val resolver: CallerNumberResolver,
    private val simInfo: SimInfoProvider,
    clock: Clock,
) {

    private val machine = CallStateMachine(clock)

    suspend fun onEvent(event: CallStateEvent) {
        val notification = machine.onEvent(event) ?: return
        val s = settings.current()
        if (!s.isConfigured) return

        when (notification) {
            is CallNotification.Incoming -> {
                if (!s.callNotificationEnabled) return
                val number = event.number ?: resolver.resolve(sinceMillis = event.atMillis)
                sender.sendOrEnqueue(
                    formatter.incomingCall(number, event.atMillis, simInfo.activeSim()),
                )
            }

            is CallNotification.Missed -> {
                if (!s.missedCallNotificationEnabled) return
                val number = notification.number ?: resolver.resolve(sinceMillis = machine.lastRingingAtMillis)
                sender.sendOrEnqueue(formatter.missedCall(number))
            }
        }
    }

    /** Called when monitoring restarts so a stale ring from the previous session is forgotten. */
    fun onMonitoringRestarted() = machine.reset()
}
