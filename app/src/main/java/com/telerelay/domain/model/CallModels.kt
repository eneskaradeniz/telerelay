package com.telerelay.domain.model

/** Platform call states, mapped from TelephonyManager. */
enum class CallState { IDLE, RINGING, OFFHOOK }

/**
 * One raw observation from the call monitor, before any interpretation.
 * [number] is only populated on API 26–30; on API 31+ the platform no longer
 * delivers it and it is resolved from the call log instead.
 */
data class CallStateEvent(
    val state: CallState,
    val number: String?,
    val atMillis: Long,
)

/** What the call state machine decided is worth notifying about. */
sealed interface CallNotification {
    /** A call started ringing. */
    data class Incoming(val number: String?, val atMillis: Long) : CallNotification

    /** Rang, was never answered, and went away. */
    data class Missed(val number: String?) : CallNotification
}
