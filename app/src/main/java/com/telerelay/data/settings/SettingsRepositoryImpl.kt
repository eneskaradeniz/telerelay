package com.telerelay.data.settings

import com.telerelay.domain.model.AppSettings
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

    override fun setAdFilterEnabled(enabled: Boolean) =
        update { copy(adFilterEnabled = enabled) }

    private fun load(): AppSettings = AppSettings(
        botToken = dataSource.readBotToken(),
        chatId = dataSource.readChatId(),
        smsForwardingEnabled = dataSource.readBoolean(KEY_SMS_ENABLED, true),
        callNotificationEnabled = dataSource.readBoolean(KEY_CALL_ENABLED, true),
        missedCallNotificationEnabled = dataSource.readBoolean(KEY_MISSED_ENABLED, true),
        adFilterEnabled = dataSource.readBoolean(KEY_AD_FILTER_ENABLED, true),
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
        dataSource.writeBoolean(KEY_AD_FILTER_ENABLED, s.adFilterEnabled)
    }

    private fun String.blankToNull(): String? = takeIf { isNotBlank() }

    private companion object {
        const val KEY_SMS_ENABLED = "sms_forwarding_enabled"
        const val KEY_CALL_ENABLED = "call_notification_enabled"
        const val KEY_MISSED_ENABLED = "missed_call_notification_enabled"
        const val KEY_AD_FILTER_ENABLED = "ad_filter_enabled"
    }
}
