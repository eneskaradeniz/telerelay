package com.telerelay.domain.model

/**
 * Immutable snapshot of everything the user can configure.
 * Persisted by [com.telerelay.data.settings.SettingsRepositoryImpl]; secrets
 * ([botToken], [chatId]) are encrypted at rest from the crypto phase onwards.
 */
data class AppSettings(
    val botToken: String? = null,
    val chatId: String? = null,
    val smsForwardingEnabled: Boolean = true,
    val callNotificationEnabled: Boolean = true,
    val missedCallNotificationEnabled: Boolean = true,
) {
    /** True when both Telegram credentials are present and non-blank. */
    val isConfigured: Boolean
        get() = !botToken.isNullOrBlank() && !chatId.isNullOrBlank()
}
