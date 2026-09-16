package com.telerelay.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process truth about whether [MonitorService] is running. The service itself
 * is authoritative: it marks started/stopped from onStartCommand/onDestroy, and
 * a sticky restart after process death refreshes the flag automatically —
 * because the service lives in this same process, the flag can never lie.
 */
@Singleton
class MonitorStatus @Inject constructor() {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun markStarted() {
        _running.value = true
    }

    fun markStopped() {
        _running.value = false
    }
}
