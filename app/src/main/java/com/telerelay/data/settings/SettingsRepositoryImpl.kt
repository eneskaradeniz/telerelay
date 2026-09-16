package com.telerelay.data.settings

import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.PrivacyMode
import com.telerelay.domain.port.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [AppSettings] in a [StateFlow] backed by [SettingsLocalDataSource].
 * Every setter writes through to storage and publishes a fresh snapshot.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: SettingsLocalDataSource,
) : SettingsRepository {

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun current(): AppSettings = _settings.value

    override fun setBotToken(value: String?) = update { copy(botToken = value?.blankToNull()) }
    override fun setChatId(value: String?) = update { copy(chatId = value?.blankToNull()) }

    override fun setSmsForwardingEnabled(enabled: Boolean) =
        update { copy(smsForwardingEnabled = enabled) }

    override fun setCallNotificationEnabled(enabled: Boolean) =
        update { copy(callNotificationEnabled = enabled) }

    override fun setMissedCallNotificationEnabled(enabled: Boolean) =
        update { copy(missedCallNotificationEnabled = enabled) }

    override fun setPrivacyGuardEnabled(enabled: Boolean) =
        update { copy(privacyGuardEnabled = enabled) }

    override fun setPrivacyMode(mode: PrivacyMode) = update { copy(privacyMode = mode) }

    override fun setFilterPatterns(patterns: List<String>) =
        update { copy(filterPatterns = patterns.filter { it.isNotBlank() }) }

    override fun setExcludedNumbers(numbers: List<String>) =
        update { copy(excludedNumbers = numbers.filter { it.isNotBlank() }) }

    override fun setLanguageTag(tag: String?) = update { copy(languageTag = tag?.blankToNull()) }

    private fun load(): AppSettings = AppSettings(
        botToken = dataSource.readBotToken(),
        chatId = dataSource.readChatId(),
        smsForwardingEnabled = dataSource.readBoolean(KEY_SMS_ENABLED, true),
        callNotificationEnabled = dataSource.readBoolean(KEY_CALL_ENABLED, true),
        missedCallNotificationEnabled = dataSource.readBoolean(KEY_MISSED_ENABLED, true),
        privacyGuardEnabled = dataSource.readBoolean(KEY_PRIVACY_ENABLED, true),
        privacyMode = dataSource.readString(KEY_PRIVACY_MODE)?.toEnum<PrivacyMode>() ?: PrivacyMode.MASK,
        filterPatterns = dataSource.readStringList(KEY_FILTER_PATTERNS)
            .ifEmpty { AppSettings().filterPatterns },
        excludedNumbers = dataSource.readStringList(KEY_EXCLUDED_NUMBERS),
        languageTag = dataSource.readString(KEY_LANGUAGE_TAG),
    )

    private fun update(transform: AppSettings.() -> AppSettings) {
        val next = _settings.value.transform()
        persist(next)
        _settings.value = next
    }

    private fun persist(s: AppSettings) {
        dataSource.writeBotToken(s.botToken)
        dataSource.writeChatId(s.chatId)
        dataSource.writeBoolean(KEY_SMS_ENABLED, s.smsForwardingEnabled)
        dataSource.writeBoolean(KEY_CALL_ENABLED, s.callNotificationEnabled)
        dataSource.writeBoolean(KEY_MISSED_ENABLED, s.missedCallNotificationEnabled)
        dataSource.writeBoolean(KEY_PRIVACY_ENABLED, s.privacyGuardEnabled)
        dataSource.writeString(KEY_PRIVACY_MODE, s.privacyMode.name)
        dataSource.writeStringList(KEY_FILTER_PATTERNS, s.filterPatterns)
        dataSource.writeStringList(KEY_EXCLUDED_NUMBERS, s.excludedNumbers)
        dataSource.writeString(KEY_LANGUAGE_TAG, s.languageTag)
    }

    private fun String.blankToNull(): String? = takeIf { isNotBlank() }

    private inline fun <reified T : Enum<T>> String.toEnum(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()

    private companion object {
        const val KEY_SMS_ENABLED = "sms_forwarding_enabled"
        const val KEY_CALL_ENABLED = "call_notification_enabled"
        const val KEY_MISSED_ENABLED = "missed_call_notification_enabled"
        const val KEY_PRIVACY_ENABLED = "privacy_guard_enabled"
        const val KEY_PRIVACY_MODE = "privacy_mode"
        const val KEY_FILTER_PATTERNS = "filter_patterns"
        const val KEY_EXCLUDED_NUMBERS = "excluded_numbers"
        const val KEY_LANGUAGE_TAG = "language_tag"
    }
}
