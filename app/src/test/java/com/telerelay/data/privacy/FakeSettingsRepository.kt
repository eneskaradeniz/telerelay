package com.telerelay.data.privacy

import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.PrivacyMode
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

    override fun setPrivacyGuardEnabled(enabled: Boolean) =
        update { copy(privacyGuardEnabled = enabled) }

    override fun setPrivacyMode(mode: PrivacyMode) = update { copy(privacyMode = mode) }
    override fun setFilterPatterns(patterns: List<String>) = update { copy(filterPatterns = patterns) }
    override fun setExcludedNumbers(numbers: List<String>) = update { copy(excludedNumbers = numbers) }
    override fun setLanguageTag(tag: String?) = update { copy(languageTag = tag) }

    private fun update(transform: AppSettings.() -> AppSettings) {
        flow.value = flow.value.transform()
    }
}
