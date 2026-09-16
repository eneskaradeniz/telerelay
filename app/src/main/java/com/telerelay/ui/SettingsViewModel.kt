package com.telerelay.ui

import android.content.Context
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.FailureReason
import com.telerelay.domain.model.PrivacyMode
import com.telerelay.domain.model.SendOutcome
import com.telerelay.domain.port.MessageFormatter
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.domain.port.ServiceController
import com.telerelay.domain.port.TelegramGateway
import com.telerelay.service.MonitorStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the settings screen. Persists everything immediately —
 * there is no save button — and exposes one-shot actions for test sends,
 * service control and language switching.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serviceController: ServiceController,
    private val gateway: TelegramGateway,
    private val formatter: MessageFormatter,
    private val monitorStatus: MonitorStatus,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val testResult = MutableStateFlow<TestResult?>(null)
    private val permissions = MutableStateFlow(PermissionGroup.missing(appContext))
    private val languageTag = MutableStateFlow(currentLanguageTag())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        permissions.asStateFlow(),
        monitorStatus.running,
        testResult.asStateFlow(),
        languageTag.asStateFlow(),
    ) { settings, missing, running, test, lang ->
        SettingsUiState(
            settings = settings,
            missingPermissions = missing,
            callMonitoringRunning = running,
            batteryExempt = isBatteryExempt(),
            testResult = test,
            languageTag = lang,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialUiState())

    /** Re-evaluates permission grants (e.g. after returning from the system dialog). */
    fun refreshPermissions() {
        permissions.value = PermissionGroup.missing(appContext)
    }

    // --- credentials -------------------------------------------------------

    fun setBotToken(value: String) = settingsRepository.setBotToken(value.trim())
    fun setChatId(value: String) = settingsRepository.setChatId(value.trim())

    fun sendTestMessage() {
        val s = settingsRepository.current()
        if (!s.isConfigured) {
            testResult.value = TestResult.Failure(reason = FailureReason.NOT_CONFIGURED, notConfigured = true)
            return
        }
        viewModelScope.launch {
            testResult.value = TestResult.Sending
            testResult.value = when (val outcome = gateway.send(formatter.testMessage())) {
                is SendOutcome.Sent -> TestResult.Success
                is SendOutcome.Failed -> TestResult.Failure(reason = outcome.reason)
                is SendOutcome.RetryLater -> TestResult.Failure(reason = null)
            }
        }
    }

    // --- toggles -----------------------------------------------------------

    fun setSmsForwardingEnabled(enabled: Boolean) = settingsRepository.setSmsForwardingEnabled(enabled)

    /** Toggling call notifications also starts/stops the foreground service. */
    fun setCallNotificationEnabled(enabled: Boolean) {
        settingsRepository.setCallNotificationEnabled(enabled)
        if (enabled) serviceController.startCallMonitoring() else serviceController.stopCallMonitoring()
    }

    fun setMissedCallNotificationEnabled(enabled: Boolean) =
        settingsRepository.setMissedCallNotificationEnabled(enabled)

    // --- privacy guard -----------------------------------------------------

    fun setPrivacyGuardEnabled(enabled: Boolean) = settingsRepository.setPrivacyGuardEnabled(enabled)
    fun setPrivacyMode(mode: PrivacyMode) = settingsRepository.setPrivacyMode(mode)
    fun addFilterPattern(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return
        settingsRepository.setFilterPatterns(settingsRepository.current().filterPatterns + trimmed)
    }

    fun removeFilterPattern(pattern: String) =
        settingsRepository.setFilterPatterns(settingsRepository.current().filterPatterns - pattern)

    fun addExcludedNumber(number: String) {
        val normalized = number.trim().replace(" ", "")
        if (normalized.isEmpty()) return
        settingsRepository.setExcludedNumbers(settingsRepository.current().excludedNumbers + normalized)
    }

    fun removeExcludedNumber(number: String) =
        settingsRepository.setExcludedNumbers(settingsRepository.current().excludedNumbers - number)

    // --- service -----------------------------------------------------------

    fun startCallMonitoring() = serviceController.startCallMonitoring()
    fun stopCallMonitoring() = serviceController.stopCallMonitoring()

    // --- language ----------------------------------------------------------

    /** @param tag "tr", "en", or null to follow the system language. */
    fun setLanguage(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag),
        )
        languageTag.value = tag
    }

    private fun initialUiState() = SettingsUiState(
        settings = settingsRepository.current(),
        missingPermissions = PermissionGroup.missing(appContext),
        callMonitoringRunning = monitorStatus.running.value,
        batteryExempt = isBatteryExempt(),
        languageTag = currentLanguageTag(),
    )

    private fun isBatteryExempt(): Boolean {
        val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    private fun currentLanguageTag(): String? =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.toLanguageTags()
}
