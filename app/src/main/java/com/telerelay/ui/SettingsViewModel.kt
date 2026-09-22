package com.telerelay.ui

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.FailureReason
import com.telerelay.domain.model.OutgoingMessage
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
 * there is no save button — and exposes one-shot actions for test sends and
 * service control.
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
    private val requiredPermissions = MutableStateFlow(PermissionGroup.missingRequired(appContext))
    private val batteryExempt = MutableStateFlow(isBatteryExempt())

    init {
        // Auto-convergence: toggling call notifications on BEFORE the
        // prerequisites are met only records the desire; the moment they become
        // met (Telegram configured, permissions granted) the service starts by
        // itself — no second toggle needed. A manual Stop is respected: the
        // convergence fires on the ready-state *transition* only, never while
        // ready stays true.
        viewModelScope.launch {
            var previousReady = isCallMonitoringReady()
            combine(
                requiredPermissions.asStateFlow(),
                monitorStatus.running,
                settingsRepository.settings,
            ) { missingRequired, running, s ->
                Triple(s.callNotificationEnabled, missingRequired.isEmpty() && s.isConfigured, running)
            }.collect { (toggleOn, ready, running) ->
                if (shouldAutoStartCallMonitoring(previousReady, ready, toggleOn, running)) {
                    serviceController.startCallMonitoring()
                }
                previousReady = ready
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        permissions.asStateFlow(),
        requiredPermissions.asStateFlow(),
        monitorStatus.running,
        testResult.asStateFlow(),
    ) { settings, missing, missingRequired, running, test ->
        SettingsUiState(
            settings = settings,
            missingPermissions = missing,
            missingRequiredPermissions = missingRequired,
            callMonitoringRunning = running,
            testResult = test,
            botTokenPreview = tokenPreview(settings.botToken),
            batteryExempt = batteryExempt.value,
        )
    }
        // Battery state lives in its own flow: re-evaluating it only inside the
        // combine above left the UI stale after the system dialog, because an
        // unchanged permissions flow emits nothing on ON_RESUME.
        .combine(batteryExempt.asStateFlow()) { state, exempt ->
            state.copy(batteryExempt = exempt)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialUiState())

    /** Re-evaluates permission grants / battery state after system dialogs. */
    fun refreshPermissions() {
        permissions.value = PermissionGroup.missing(appContext)
        requiredPermissions.value = PermissionGroup.missingRequired(appContext)
        batteryExempt.value = isBatteryExempt()
    }

    // --- credentials -------------------------------------------------------

    /** Editing a credential invalidates a previous test outcome. */
    fun setBotToken(value: String) {
        settingsRepository.setBotToken(value.trim())
        clearTestResult()
    }

    fun setChatId(value: String) {
        settingsRepository.setChatId(value.trim())
        clearTestResult()
    }

    fun sendTestMessage() {
        val s = settingsRepository.current()
        if (!s.isConfigured) {
            testResult.value = TestResult.Failure(reason = FailureReason.NOT_CONFIGURED, notConfigured = true)
            return
        }
        viewModelScope.launch {
            testResult.value = TestResult.Sending
            testResult.value = when (val outcome = gateway.send(OutgoingMessage(formatter.testMessage()))) {
                is SendOutcome.Sent -> TestResult.Success
                is SendOutcome.Failed -> TestResult.Failure(reason = outcome.reason)
                is SendOutcome.RetryLater -> TestResult.Failure(reason = null)
            }
        }
    }

    // --- toggles -----------------------------------------------------------

    fun setSmsForwardingEnabled(enabled: Boolean) = settingsRepository.setSmsForwardingEnabled(enabled)

    /**
     * Toggling call notifications also starts/stops the foreground service —
     * but starting respects the same gate as the Start button: an
     * unconfigured/unpermitted service is useless. When the prerequisites are
     * met later, the auto-convergence collector in [init] starts it instead.
     */
    fun setCallNotificationEnabled(enabled: Boolean) {
        settingsRepository.setCallNotificationEnabled(enabled)
        if (!enabled) {
            serviceController.stopCallMonitoring()
            return
        }
        if (isCallMonitoringReady() && !monitorStatus.running.value) {
            serviceController.startCallMonitoring()
        }
    }

    fun setMissedCallNotificationEnabled(enabled: Boolean) =
        settingsRepository.setMissedCallNotificationEnabled(enabled)

    fun setAdFilterEnabled(enabled: Boolean) = settingsRepository.setAdFilterEnabled(enabled)

    // --- service -----------------------------------------------------------

    fun startCallMonitoring() = serviceController.startCallMonitoring()
    fun stopCallMonitoring() = serviceController.stopCallMonitoring()

    private fun initialUiState() = SettingsUiState(
        settings = settingsRepository.current(),
        missingPermissions = PermissionGroup.missing(appContext),
        missingRequiredPermissions = PermissionGroup.missingRequired(appContext),
        callMonitoringRunning = monitorStatus.running.value,
        batteryExempt = isBatteryExempt(),
    )

    private fun isBatteryExempt(): Boolean {
        val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    /** Masked token hint for the supporting text; never more than the last 4 chars. */
    private fun tokenPreview(token: String?): String? {
        val value = token?.takeIf { it.isNotBlank() } ?: return null
        return "••••" + value.takeLast(4)
    }

    private fun clearTestResult() {
        testResult.value = null
    }

    private fun isCallMonitoringReady(): Boolean =
        requiredPermissions.value.isEmpty() && settingsRepository.current().isConfigured

    companion object {

        /**
         * Pure decision for the auto-convergence collector, visible for tests:
         * start only when the toggle is on, the prerequisites JUST became met,
         * and the service is not already running — a deliberate Stop (ready
         * stays true, service down) is never overridden.
         */
        fun shouldAutoStartCallMonitoring(
            previousReady: Boolean,
            ready: Boolean,
            callToggleOn: Boolean,
            running: Boolean,
        ): Boolean = callToggleOn && ready && !running && !previousReady
    }
}
