package com.telerelay.domain.port

import com.telerelay.domain.model.CallStateEvent

/**
 * Observes platform call state and pushes raw [CallStateEvent]s to the given
 * callback while running. Implementations register/unregister the appropriate
 * platform listener (TelephonyCallback on API 31+, PhoneStateListener before).
 */
interface CallMonitor {
    fun start(onEvent: (CallStateEvent) -> Unit)
    fun stop()
}
