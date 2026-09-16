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
 * On API 31+ the telephony callback carries no caller number and the call-log
 * row is only written when the call ends — so the number captured from the
 * RINGING-state PHONE_STATE broadcast ([onIncomingNumber]) is the primary
 * ringing-time source here. Numbers reported on IDLE/OFFHOOK broadcasts are
 * ignored: they describe a call that has already ended, and storing them
 * would let them attach to the next call.
 *
 * Pure Kotlin, driven by an injected [Clock] in tests. Callers must serialize
 * access (the use case confines it to a single thread).
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
    private var broadcastNumber: String? = null
    private var broadcastNumberAtMillis: Long = 0L

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
                // An answered call needs no number anymore; keeping it would let
                // it leak into a later ring.
                if (answered) discardBroadcastNumber()
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
        discardBroadcastNumber()
    }

    /**
     * Remembers the caller number from the PHONE_STATE broadcast, but only
     * when the broadcast reported RINGING: a number riding an IDLE/OFFHOOK
     * broadcast belongs to a call that already ended, and storing it would
     * mislabel the next caller.
     */
    fun onIncomingNumber(state: CallState?, number: String?, atMillis: Long) {
        if (state != CallState.RINGING) return
        if (number.isNullOrBlank()) return
        broadcastNumber = number
        broadcastNumberAtMillis = atMillis
    }

    /**
     * Latest broadcast number if still usable, without consuming it — used by
     * the grace window while a ring is in progress, so the number stays
     * available for the missed notification later.
     */
    fun peekBroadcastNumber(nowMillis: Long): String? =
        broadcastNumber?.takeIf { isUsableBroadcastNumber(nowMillis, forRingStart = false) }

    /** Latest broadcast number if still usable, consuming it. */
    fun takeBroadcastNumber(nowMillis: Long, forRingStart: Boolean): String? =
        broadcastNumber
            ?.takeIf { isUsableBroadcastNumber(nowMillis, forRingStart) }
            .also { if (it != null) discardBroadcastNumber() }

    private fun discardBroadcastNumber() {
        broadcastNumber = null
        broadcastNumberAtMillis = 0L
    }

    /**
     * Usable when fresh, or — while a ring is underway (missed path, grace
     * window) — when it arrived after the current ring started: a number
     * delivered mid-ring describes this call no matter how long the ring lasts.
     * At ring START the mid-ring clause is disabled: the previous ring's
     * anchor is still in [ringingAtMillis], so a stale number there could
     * otherwise attach itself to the new call.
     */
    private fun isUsableBroadcastNumber(nowMillis: Long, forRingStart: Boolean): Boolean =
        nowMillis - broadcastNumberAtMillis <= BROADCAST_FRESHNESS_MILLIS ||
            (!forRingStart && ringingAtMillis > 0 && broadcastNumberAtMillis >= ringingAtMillis)

    private fun onRinging(previous: CallState?, event: CallStateEvent): CallNotification? {
        if (previous == CallState.RINGING || previous == CallState.OFFHOOK) return null
        val withinCooldown = lastIncomingEmitAt != null &&
            event.atMillis - lastIncomingEmitAt!! < cooldownMillis
        if (withinCooldown) return null // ring flicker

        val number = event.number ?: takeBroadcastNumber(event.atMillis, forRingStart = true)
        ringingNumber = number
        ringingAtMillis = event.atMillis
        lastIncomingEmitAt = event.atMillis
        answered = false
        return CallNotification.Incoming(number = number, atMillis = event.atMillis)
    }

    private fun onIdle(previous: CallState?, event: CallStateEvent): CallNotification? {
        val ringDuration = event.atMillis - ringingAtMillis
        val missed = previous == CallState.RINGING &&
            !answered &&
            ringDuration >= minRingDurationMillis

        answered = false
        return if (missed) {
            CallNotification.Missed(
                number = ringingNumber ?: takeBroadcastNumber(event.atMillis, forRingStart = false),
            )
        } else {
            discardBroadcastNumber()
            null
        }
    }

    companion object {
        /** RINGING→IDLE→RINGING inside this window is the same call, not two. */
        const val DEFAULT_COOLDOWN_MILLIS = 5_000L

        /** Rings shorter than this are treated as state flicker, not missed calls. */
        const val DEFAULT_MIN_RING_DURATION_MILLIS = 3_000L

        /**
         * How long a broadcast-captured number stays valid. Deliveries land
         * within ~2 s of the state change; anything older belongs to another call.
         */
        const val BROADCAST_FRESHNESS_MILLIS = 15_000L
    }
}
