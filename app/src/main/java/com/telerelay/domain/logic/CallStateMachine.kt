package com.telerelay.domain.logic

import com.telerelay.domain.model.CallNotification
import com.telerelay.domain.model.CallState
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.domain.port.Clock

/**
 * Turns a noisy stream of call-state callbacks into at most two notifications
 * per call: one [CallNotification.Incoming] when it starts ringing and one
 * [CallNotification.Missed] if it went unanswered.
 *
 * Suppression rules:
 * - duplicate RINGING deliveries (some OEMs repeat them) are ignored;
 * - RINGING while OFFHOOK is call-waiting and never a new call;
 * - OFFHOOK means answered — the subsequent IDLE is not a miss;
 * - a ring that lasted less than [minRingDurationMillis] is treated as a
 *   state flicker, not a real missed call;
 * - outgoing calls (IDLE→OFFHOOK without ringing) emit nothing.
 *
 * Pure Kotlin, driven by an injected [Clock] in tests.
 */
class CallStateMachine(
    private val clock: Clock,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val minRingDurationMillis: Long = DEFAULT_MIN_RING_DURATION_MILLIS,
) {
    private var lastState: CallState? = null
    private var ringingNumber: String? = null
    private var ringingAtMillis: Long = 0L
    private var lastIncomingEmitAt: Long? = null
    private var answered: Boolean = false

    /** When the most recent ring started — the anchor for call-log number lookups. */
    val lastRingingAtMillis: Long get() = ringingAtMillis

    /** @return the notification to forward, or null when nothing is worth sending. */
    fun onEvent(event: CallStateEvent): CallNotification? {
        val previous = lastState
        lastState = event.state

        return when (event.state) {
            CallState.RINGING -> onRinging(previous, event)
            CallState.OFFHOOK -> {
                if (previous == CallState.RINGING) answered = true
                null
            }
            CallState.IDLE -> onIdle(previous, event)
        }
    }

    /** Clears all memory; used when monitoring restarts after being stopped. */
    fun reset() {
        lastState = null
        ringingNumber = null
        ringingAtMillis = 0L
        lastIncomingEmitAt = null
        answered = false
    }

    private fun onRinging(previous: CallState?, event: CallStateEvent): CallNotification? {
        if (previous == CallState.RINGING || previous == CallState.OFFHOOK) return null
        val withinCooldown = lastIncomingEmitAt != null &&
            event.atMillis - lastIncomingEmitAt!! < cooldownMillis
        if (withinCooldown) return null // ring flicker

        ringingNumber = event.number
        ringingAtMillis = event.atMillis
        lastIncomingEmitAt = event.atMillis
        answered = false
        return CallNotification.Incoming(number = event.number, atMillis = event.atMillis)
    }

    private fun onIdle(previous: CallState?, event: CallStateEvent): CallNotification? {
        val ringDuration = event.atMillis - ringingAtMillis
        val missed = previous == CallState.RINGING &&
            !answered &&
            ringDuration >= minRingDurationMillis

        answered = false
        return if (missed) CallNotification.Missed(number = ringingNumber) else null
    }

    companion object {
        /** RINGING→IDLE→RINGING inside this window is the same call, not two. */
        const val DEFAULT_COOLDOWN_MILLIS = 5_000L

        /** Rings shorter than this are treated as state flicker, not missed calls. */
        const val DEFAULT_MIN_RING_DURATION_MILLIS = 3_000L
    }
}
