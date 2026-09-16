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
    /** All not-yet-granted groups — rendered as request rows. */
    val missingPermissions: List<PermissionGroup> = emptyList(),
    /** The subset that gates starting the call-monitoring service (see [PermissionGroup.requiredForService]). */
    val missingRequiredPermissions: List<PermissionGroup> = emptyList(),
    val callMonitoringRunning: Boolean = false,
    val batteryExempt: Boolean = false,
    val testResult: TestResult? = null,
    /** Non-null when a token is stored: masked hint like "••••a1b2" — never the token itself. */
    val botTokenPreview: String? = null,
)
