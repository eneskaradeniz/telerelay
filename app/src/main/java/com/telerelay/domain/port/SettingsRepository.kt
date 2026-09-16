package com.telerelay.domain.port

import com.telerelay.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for [AppSettings]. Backed by a [StateFlow] so both the
 * UI and one-shot consumers (receivers) read the same snapshot without suspend.
 */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /** Synchronous snapshot — safe from broadcast receivers on any thread. */
    fun current(): AppSettings = settings.value

    fun setBotToken(value: String?)
    fun setChatId(value: String?)
    fun setSmsForwardingEnabled(enabled: Boolean)
    fun setCallNotificationEnabled(enabled: Boolean)
    fun setMissedCallNotificationEnabled(enabled: Boolean)
}
