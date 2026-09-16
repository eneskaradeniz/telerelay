package com.telerelay.domain.port

/**
 * Starts/stops the foreground call-monitoring service from the UI layer
 * without the presentation code touching Android service APIs.
 */
interface ServiceController {
    fun startCallMonitoring()
    fun stopCallMonitoring()
}
