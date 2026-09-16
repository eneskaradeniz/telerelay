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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.telerelay.domain.model.PrivacyMode

/**
 * The single screen of the app. Sections, top to bottom: permissions,
 * Telegram credentials, forwarding toggles, privacy guard, service control,
 * language. Every change is persisted immediately.
 */
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionsSection(
                missing = state.missingPermissions,
                onGrant = {
                    val toRequest = it.permissions.toTypedArray()
                    permissionLauncher.launch(toRequest)
                },
            )

            CredentialsSection(
                token = state.settings.botToken.orEmpty(),
                chatId = state.settings.chatId.orEmpty(),
                testResult = state.testResult,
                onTokenChange = viewModel::setBotToken,
                onChatIdChange = viewModel::setChatId,
                onTest = viewModel::sendTestMessage,
            )

            ForwardingSection(
                smsEnabled = state.settings.smsForwardingEnabled,
                callsEnabled = state.settings.callNotificationEnabled,
                missedEnabled = state.settings.missedCallNotificationEnabled,
                onSmsChange = viewModel::setSmsForwardingEnabled,
                onCallsChange = viewModel::setCallNotificationEnabled,
                onMissedChange = viewModel::setMissedCallNotificationEnabled,
            )

            PrivacyGuardSection(
                enabled = state.settings.privacyGuardEnabled,
                mode = state.settings.privacyMode,
                patterns = state.settings.filterPatterns,
                excludedNumbers = state.settings.excludedNumbers,
                onEnabledChange = viewModel::setPrivacyGuardEnabled,
                onModeChange = viewModel::setPrivacyMode,
                onAddPattern = viewModel::addFilterPattern,
                onRemovePattern = viewModel::removeFilterPattern,
                onAddNumber = viewModel::addExcludedNumber,
                onRemoveNumber = viewModel::removeExcludedNumber,
            )

            ServiceSection(
                running = state.callMonitoringRunning,
                canStart = state.missingPermissions.isEmpty() && state.settings.isConfigured,
                batteryExempt = state.batteryExempt,
                onStart = viewModel::startCallMonitoring,
                onStop = viewModel::stopCallMonitoring,
            )

            LanguageSection(
                currentTag = state.languageTag,
                onSelect = viewModel::setLanguage,
            )

            Text(
                text = stringResource(R.string.footer_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

@Composable
private fun PermissionsSection(
    missing: List<PermissionGroup>,
    onGrant: (PermissionGroup) -> Unit,
) {
    SectionCard(title = stringResource(R.string.perm_section_title)) {
        if (missing.isEmpty()) {
            Text(stringResource(R.string.perm_all_granted), style = MaterialTheme.typography.bodyMedium)
        } else {
            missing.forEach { group ->
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(group.titleRes), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(group.rationaleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onGrant(group) }) {
                        Text(stringResource(R.string.perm_action_grant))
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialsSection(
    token: String,
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
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            if (showToken) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                        ),
                        contentDescription = null,
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
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onTest) { Text(stringResource(R.string.action_test)) }
        TestResultRow(testResult)
    }
}

@Composable
private fun TestResultRow(testResult: TestResult?) {
    when (testResult) {
        is TestResult.Sending -> StatusText(stringResource(R.string.test_sending), MaterialTheme.colorScheme.onSurfaceVariant)
        is TestResult.Success -> StatusText(stringResource(R.string.test_ok), MaterialTheme.colorScheme.primary)
        is TestResult.Failure -> {
            val text = when (testResult.reason) {
                FailureReason.INVALID_TOKEN -> R.string.test_failed_token
                FailureReason.INVALID_CHAT -> R.string.test_failed_chat
                FailureReason.NOT_CONFIGURED -> R.string.test_not_configured
                FailureReason.REJECTED, null -> R.string.test_failed_network
            }
            StatusText(stringResource(text), MaterialTheme.colorScheme.error)
        }
        null -> Unit
    }
}

@Composable
private fun ForwardingSection(
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
            onChange = onSmsChange,
        )
        ToggleRow(
            label = stringResource(R.string.toggle_calls),
            hint = stringResource(R.string.toggle_calls_hint),
            checked = callsEnabled,
            onChange = onCallsChange,
        )
        ToggleRow(
            label = stringResource(R.string.toggle_missed),
            hint = null,
            checked = missedEnabled,
            onChange = onMissedChange,
        )
    }
}

@Composable
private fun PrivacyGuardSection(
    enabled: Boolean,
    mode: PrivacyMode,
    patterns: List<String>,
    excludedNumbers: List<String>,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (PrivacyMode) -> Unit,
    onAddPattern: (String) -> Unit,
    onRemovePattern: (String) -> Unit,
    onAddNumber: (String) -> Unit,
    onRemoveNumber: (String) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_privacy)) {
        ToggleRow(
            label = stringResource(R.string.toggle_privacy),
            hint = null,
            checked = enabled,
            onChange = onEnabledChange,
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == PrivacyMode.MASK,
                onClick = { onModeChange(PrivacyMode.MASK) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = enabled,
            ) { Text(stringResource(R.string.privacy_mode_mask)) }
            SegmentedButton(
                selected = mode == PrivacyMode.EXCLUDE,
                onClick = { onModeChange(PrivacyMode.EXCLUDE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = enabled,
            ) { Text(stringResource(R.string.privacy_mode_exclude)) }
        }

        ListEditor(
            title = stringResource(R.string.privacy_patterns),
            items = patterns,
            emptyText = stringResource(R.string.privacy_empty),
            addLabel = stringResource(R.string.privacy_field_pattern),
            addActionLabel = stringResource(R.string.privacy_add),
            removeLabel = stringResource(R.string.privacy_remove),
            enabled = enabled,
            onAdd = onAddPattern,
            onRemove = onRemovePattern,
        )
        ListEditor(
            title = stringResource(R.string.privacy_numbers),
            items = excludedNumbers,
            emptyText = stringResource(R.string.privacy_empty),
            addLabel = stringResource(R.string.privacy_field_number),
            addActionLabel = stringResource(R.string.privacy_add),
            removeLabel = stringResource(R.string.privacy_remove),
            enabled = enabled,
            onAdd = onAddNumber,
            onRemove = onRemoveNumber,
        )
    }
}

@Composable
private fun ServiceSection(
    running: Boolean,
    canStart: Boolean,
    batteryExempt: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.section_service)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(
                    when {
                        running -> R.string.service_status_active
                        canStart -> R.string.service_status_stopped
                        else -> R.string.service_status_blocked
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = canStart && !running) {
                Text(stringResource(R.string.action_start))
            }
            OutlinedButton(onClick = onStop, enabled = running) {
                Text(stringResource(R.string.action_stop))
            }
        }

        if (!batteryExempt) {
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }) { Text(stringResource(R.string.battery_request)) }
        } else {
            Text(
                stringResource(R.string.battery_ok),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageSection(currentTag: String?, onSelect: (String?) -> Unit) {
    SectionCard(title = stringResource(R.string.section_language)) {
        val options = listOf(
            null to stringResource(R.string.lang_system),
            "tr" to stringResource(R.string.lang_tr),
            "en" to stringResource(R.string.lang_en),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (tag, label) ->
                SegmentedButton(
                    selected = currentTag == tag,
                    onClick = { onSelect(tag) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Shared list editor for regex patterns and excluded numbers: a simple
 * add-via-dialog, remove-per-row list. Deliberately plain — the value is in
 * the semantics, not the chrome.
 */
@Composable
private fun ListEditor(
    title: String,
    items: List<String>,
    emptyText: String,
    addLabel: String,
    addActionLabel: String,
    removeLabel: String,
    enabled: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (items.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(item) }, enabled = enabled) {
                        Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_delete), contentDescription = removeLabel)
                    }
                }
            }
        }
        TextButton(onClick = { dialogOpen = true }, enabled = enabled) { Text(addActionLabel) }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(addLabel) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAdd(draft)
                        draft = ""
                        dialogOpen = false
                    },
                    enabled = draft.isNotBlank(),
                ) { Text(stringResource(R.string.privacy_add)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun StatusDot(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}
