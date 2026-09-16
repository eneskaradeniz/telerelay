package com.telerelay.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telerelay.R
import com.telerelay.domain.model.FailureReason
import com.telerelay.ui.components.InlineStatus
import com.telerelay.ui.components.InlineStatusState
import com.telerelay.ui.components.SectionCard
import com.telerelay.ui.components.StatusDot
import com.telerelay.ui.components.ToggleRow

/**
 * The single screen, grouped "Calm Groups" style: one status card first
 * (is it running? what is blocked?), then permissions, forwarding toggles and
 * Telegram credentials — four flat, identical cards. Every change is persisted
 * immediately; there is no save button. The app language follows the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check grants / battery state when the user returns from system dialogs.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ServiceStatusCard(
                    running = state.callMonitoringRunning,
                    missingRequiredPermissions = state.missingRequiredPermissions,
                    isConfigured = state.settings.isConfigured,
                    batteryExempt = state.batteryExempt,
                    onStart = viewModel::startCallMonitoring,
                    onStop = viewModel::stopCallMonitoring,
                )

                PermissionsCard(
                    missing = state.missingPermissions,
                    onGrant = { permissionLauncher.launch(it.permissions.toTypedArray()) },
                )

                ForwardingCard(
                    smsEnabled = state.settings.smsForwardingEnabled,
                    callsEnabled = state.settings.callNotificationEnabled,
                    missedEnabled = state.settings.missedCallNotificationEnabled,
                    onSmsChange = viewModel::setSmsForwardingEnabled,
                    onCallsChange = viewModel::setCallNotificationEnabled,
                    onMissedChange = viewModel::setMissedCallNotificationEnabled,
                )

                TelegramCard(
                    token = state.settings.botToken.orEmpty(),
                    tokenPreview = state.botTokenPreview,
                    chatId = state.settings.chatId.orEmpty(),
                    testResult = state.testResult,
                    onTokenChange = viewModel::setBotToken,
                    onChatIdChange = viewModel::setChatId,
                    onTest = viewModel::sendTestMessage,
                )

                Text(
                    text = stringResource(R.string.footer_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun ServiceStatusCard(
    running: Boolean,
    missingRequiredPermissions: List<PermissionGroup>,
    isConfigured: Boolean,
    batteryExempt: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    var batteryRequestFailed by rememberSaveable { mutableStateOf(false) }
    val canStart = missingRequiredPermissions.isEmpty() && isConfigured

    SectionCard(title = stringResource(R.string.section_service)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(
                color = when {
                    running -> MaterialTheme.colorScheme.primary
                    canStart -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = stringResource(
                    when {
                        running -> R.string.service_status_active
                        canStart -> R.string.service_status_stopped
                        else -> R.string.service_status_blocked
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // One caption per blocking reason — never color alone.
        if (!canStart) {
            if (missingRequiredPermissions.isNotEmpty()) {
                BlockReason(stringResource(R.string.status_reason_perms))
            }
            if (!isConfigured) {
                BlockReason(stringResource(R.string.status_reason_config))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = canStart && !running, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_start))
            }
            OutlinedButton(onClick = onStop, enabled = running, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_stop))
            }
        }

        // Battery exemption row: title + state text, trailing action when needed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.battery_row_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val supporting: String? = when {
                    batteryExempt -> stringResource(R.string.battery_ok)
                    batteryRequestFailed -> stringResource(R.string.battery_request_failed)
                    else -> null
                }
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (batteryRequestFailed && !batteryExempt) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (!batteryExempt) {
                TextButton(onClick = {
                    // Requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in the manifest;
                    // without it the system throws SecurityException here.
                    batteryRequestFailed = runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }.isFailure
                }) { Text(stringResource(R.string.action_exempt)) }
            }
        }
    }
}

@Composable
private fun BlockReason(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PermissionsCard(
    missing: List<PermissionGroup>,
    onGrant: (PermissionGroup) -> Unit,
) {
    SectionCard(title = stringResource(R.string.perm_section_title)) {
        if (missing.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.perm_all_granted), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            missing.forEach { group ->
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(group.titleRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(group.rationaleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    FilledTonalButton(onClick = { onGrant(group) }) {
                        Text(stringResource(R.string.perm_action_grant))
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramCard(
    token: String,
    tokenPreview: String?,
    chatId: String,
    testResult: TestResult?,
    onTokenChange: (String) -> Unit,
    onChatIdChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_credentials)) {
        var showToken by rememberSaveable { mutableStateOf(false) }
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text(stringResource(R.string.field_bot_token)) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            supportingText = tokenPreview?.let { preview -> { Text(stringResource(R.string.field_token_saved, preview)) } },
            keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Ascii),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        painter = painterResource(if (showToken) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(R.string.action_toggle_token_visibility),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = chatId,
            onValueChange = onChatIdChange,
            label = { Text(stringResource(R.string.field_chat_id)) },
            singleLine = true,
            // Chat IDs are numeric but can be negative (groups): plain ASCII, not Number.
            keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onTest, enabled = testResult !is TestResult.Sending, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_test))
        }
        TestResultRow(testResult)
    }
}

@Composable
private fun TestResultRow(testResult: TestResult?) {
    when (testResult) {
        TestResult.Sending -> InlineStatus(
            text = stringResource(R.string.test_sending),
            state = InlineStatusState.IN_PROGRESS,
        )
        TestResult.Success -> InlineStatus(
            text = stringResource(R.string.test_ok),
            state = InlineStatusState.SUCCESS,
        )
        is TestResult.Failure -> {
            val text = when (testResult.reason) {
                FailureReason.INVALID_TOKEN -> R.string.test_failed_token
                FailureReason.INVALID_CHAT -> R.string.test_failed_chat
                FailureReason.NOT_CONFIGURED -> R.string.test_not_configured
                FailureReason.REJECTED, null -> R.string.test_failed_network
            }
            InlineStatus(text = stringResource(text), state = InlineStatusState.ERROR)
        }
        null -> Unit
    }
}

@Composable
private fun ForwardingCard(
    smsEnabled: Boolean,
    callsEnabled: Boolean,
    missedEnabled: Boolean,
    onSmsChange: (Boolean) -> Unit,
    onCallsChange: (Boolean) -> Unit,
    onMissedChange: (Boolean) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_forwarding)) {
        ToggleRow(
            label = stringResource(R.string.toggle_sms),
            hint = stringResource(R.string.toggle_sms_hint),
            checked = smsEnabled,
            onCheckedChange = onSmsChange,
        )
        ToggleRow(
            label = stringResource(R.string.toggle_calls),
            hint = stringResource(R.string.toggle_calls_hint),
            checked = callsEnabled,
            onCheckedChange = onCallsChange,
        )
        ToggleRow(
            label = stringResource(R.string.toggle_missed),
            hint = stringResource(R.string.toggle_missed_hint),
            checked = missedEnabled,
            onCheckedChange = onMissedChange,
        )
    }
}
