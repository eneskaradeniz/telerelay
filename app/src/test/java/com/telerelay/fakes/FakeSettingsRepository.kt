package com.telerelay.fakes

import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.port.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mutable in-memory settings for unit tests. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val flow = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = flow.asStateFlow()

    fun set(settings: AppSettings) {
        flow.value = settings
    }

    override fun current(): AppSettings = flow.value

    override fun setBotToken(value: String?) = update { copy(botToken = value) }
    override fun setChatId(value: String?) = update { copy(chatId = value) }
    override fun setSmsForwardingEnabled(enabled: Boolean) =
        update { copy(smsForwardingEnabled = enabled) }

    override fun setCallNotificationEnabled(enabled: Boolean) =
        update { copy(callNotificationEnabled = enabled) }

    override fun setMissedCallNotificationEnabled(enabled: Boolean) =
        update { copy(missedCallNotificationEnabled = enabled) }

    override fun setAdFilterEnabled(enabled: Boolean) =
        update { copy(adFilterEnabled = enabled) }

    private fun update(transform: AppSettings.() -> AppSettings) {
        flow.value = flow.value.transform()
    }
}
