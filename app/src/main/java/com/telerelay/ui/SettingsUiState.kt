package com.telerelay.ui

import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.FailureReason

/** Outcome of the "send test message" action. */
sealed interface TestResult {
    data object Sending : TestResult
    data object Success : TestResult
    data class Failure(val reason: FailureReason?, val notConfigured: Boolean = false) : TestResult
}

/** Everything the settings screen renders. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val missingPermissions: List<PermissionGroup> = emptyList(),
    val callMonitoringRunning: Boolean = false,
    val batteryExempt: Boolean = false,
    val testResult: TestResult? = null,
    /** BCP-47 tag of the in-app language override; null = follow system. */
    val languageTag: String? = null,
)
