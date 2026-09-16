package com.telerelay.domain.model

/** What the privacy guard does with a message matching a rule. */
enum class PrivacyMode {
    /** Replace detected secrets with bullets and forward the rest. */
    MASK,

    /** Drop the message entirely. */
    EXCLUDE,
}

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
    val privacyGuardEnabled: Boolean = true,
    val privacyMode: PrivacyMode = PrivacyMode.MASK,
    val filterPatterns: List<String> = DefaultFilterRules.PATTERNS,
    val excludedNumbers: List<String> = emptyList(),
    /** BCP-47 tag ("tr", "en") or null to follow the system language. */
    val languageTag: String? = null,
) {
    /** True when both Telegram credentials are present and non-blank. */
    val isConfigured: Boolean
        get() = !botToken.isNullOrBlank() && !chatId.isNullOrBlank()
}
