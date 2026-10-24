package com.miftah.core.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miftah.core.execution.AdbState
import com.miftah.core.execution.ShellCommand
import com.miftah.core.model.*
import java.text.DateFormat
import java.util.Date

class Actions(
    val openAppInfo: (String) -> Unit,
    val uninstallDialog: (String) -> Unit,
    val share: (String, String) -> Unit,
    val openLocale: () -> Unit,
)

@Composable
fun MiftahApp(vm: MainViewModel, actions: Actions) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(
        R.string.tab_dashboard, R.string.tab_adb, R.string.tab_capabilities, R.string.tab_apps, R.string.tab_plans,
        R.string.tab_terminal, R.string.tab_audit, R.string.tab_diag, R.string.tab_settings, R.string.tab_extensions,
    )
    val dark = when (state.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) }) }
                }
                when (tab) {
                    0 -> Gated(state, vm::refresh) { Dashboard(state) }
                    1 -> AdbScreen(state, vm)
                    2 -> CapabilityList(state.capabilities)
                    3 -> Gated(state, vm::refresh) { AppList(state, vm) }
                    4 -> PlansScreen(state, vm)
                    5 -> TerminalScreen(state, vm)
                    6 -> AuditScreen(state, vm, actions)
                    7 -> DiagnosticsScreen(state, vm, actions)
                    8 -> SettingsScreen(state, vm, actions)
                    else -> ExtensionsScreen()
                }
            }
            Dialogs(state, vm, actions)
        }
    }
}

@Composable
private fun Gated(s: UiState, onRetry: () -> Unit, content: @Composable () -> Unit) {
    when {
        s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        s.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.error_load), style = MaterialTheme.typography.titleMedium)
            Text(s.error, Modifier.padding(vertical = 8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        else -> content()
    }
}

@Composable
private fun Dashboard(s: UiState) {
    val d = s.device
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val level = if (s.level == AccessLevel.ADB) R.string.access_level_adb else R.string.access_level_standard
            Text(stringResource(level), style = MaterialTheme.typography.titleMedium)
        }
        item { Text(stringResource(R.string.adb_state_label, stringResource(adbStateName(s.adbState)))) }
        if (d != null) {
            item { Text("${d.manufacturer} ${d.model}") }
            item { Text(stringResource(R.string.android_version, d.release, d.sdkInt)) }
            item { Text(stringResource(R.string.abis, d.abis.joinToString())) }
        }
        item {
            val inst = s.apps.filter { it.installed }
            Text(stringResource(R.string.apps_found, inst.size, inst.count { !it.isSystem }, inst.count { it.isSystem }))
        }
        item {
            val c = s.capabilities
            Text(stringResource(R.string.cap_summary, c.count { it.status is CapabilityStatus.Supported }, c.size))
        }
        item { Text(stringResource(R.string.recent_ops), style = MaterialTheme.typography.titleSmall) }
        if (s.audit.entries.isEmpty()) {
            item { Text(stringResource(R.string.no_ops)) }
        } else {
            items(s.audit.entries.take(3), key = { it.result.operationId }) { e ->
                Text("${e.result.name} • ${e.target} • ${stringResource(outcomeLabel(e.result.outcome))}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CapabilityList(list: List<Capability>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { c ->
            Column {
                Text(stringResource(capName(c.id)), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(statusLabel(c.status)))
                reasonOf(c.status)?.let { Text(stringResource(reasonText(it)), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun AppList(s: UiState, vm: MainViewModel) {
    val shown = remember(s.apps, s.query, s.filter) { AppQuery.apply(s.apps, s.query, s.filter) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = s.query, onValueChange = vm::setQuery, singleLine = true,
            label = { Text(stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilter.entries.forEach { f ->
                FilterChip(selected = s.filter == f, onClick = { vm.setFilter(f) }, label = { Text(stringResource(filterName(f))) })
            }
        }
        if (s.selection.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plan_selected, s.selection.size))
                TextButton(onClick = vm::clearSelection) { Text(stringResource(R.string.plan_clear_btn)) }
            }
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.no_results), Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(shown, key = { it.packageName }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { vm.selectApp(a) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = a.packageName in s.selection, onCheckedChange = { vm.toggleSelect(a.packageName) })
                        Column(Modifier.weight(1f)) {
                            Text(a.label, style = MaterialTheme.typography.titleSmall)
                            Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                            Text("${a.versionName ?: "-"} (${a.versionCode})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Dialogs(s: UiState, vm: MainViewModel, actions: Actions) {
    if (s.busy) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(stringResource(R.string.working)) }, text = { CircularProgressIndicator() })
    }
    s.lastResult?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            confirmButton = { TextButton(onClick = vm::dismissResult) { Text(stringResource(R.string.close)) } },
            dismissButton = if (s.lastUndo != null) {
                { TextButton(onClick = vm::askUndo) { Text(stringResource(R.string.undo_btn)) } }
            } else null,
            title = { Text(stringResource(resultTitle(r))) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val unknown = stringResource(R.string.unknown)
                    Text(stringResource(R.string.result_details, r.exitCode?.toString() ?: unknown, r.previousState ?: unknown, r.verifiedState ?: unknown))
                    Text(stringResource(rollbackText(r.rollback)))
                    if (r.stderr.isNotBlank()) Text(r.stderr, style = MaterialTheme.typography.bodySmall)
                    if (r.stdout.isNotBlank()) Text(r.stdout, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
    s.pending?.let { PendingDialog(it, vm) }
    s.plan.approval?.let { a ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = { vm.answerApproval(true) }) { Text(stringResource(R.string.approve)) } },
            dismissButton = { TextButton(onClick = { vm.answerApproval(false) }) { Text(stringResource(R.string.stop_plan)) } },
            title = { Text(stringResource(R.string.plan_step_title, a.index, a.total)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.plan_step_body))
                    Text(stringResource(R.string.command_label, a.commandText), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
    s.terminal.confirmCommand?.let { c ->
        AlertDialog(
            onDismissRequest = vm::cancelTerminal,
            confirmButton = { TextButton(onClick = vm::confirmTerminal) { Text(stringResource(R.string.term_run_anyway)) } },
            dismissButton = { TextButton(onClick = vm::cancelTerminal) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.term_risky_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.term_risky_body))
                }
            },
        )
    }
    s.audit.exportPreview?.let { text ->
        PreviewDialog(text, onShare = { actions.share(text, s.audit.exportMime) }, onClose = vm::dismissAuditExport)
    }
    s.diag.preview?.let { text ->
        PreviewDialog(text, onShare = { actions.share(text, "text/plain") }, onClose = vm::dismissDiagPreview)
    }
    if (s.audit.confirmClear) {
        AlertDialog(
            onDismissRequest = vm::cancelClearAudit,
            confirmButton = { TextButton(onClick = vm::confirmClearAudit) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = vm::cancelClearAudit) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.audit_clear)) },
            text = { Text(stringResource(R.string.audit_clear_confirm)) },
        )
    }
    val app = s.selectedApp
    if (app != null && s.pending == null && s.lastResult == null && !s.busy) AppDetailDialog(app, s, vm, actions)
}

@Composable
private fun PreviewDialog(text: String, onShare: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.share)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.preview_note), style = MaterialTheme.typography.bodySmall)
                Text(text.take(6000), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun PendingDialog(p: PendingOp, vm: MainViewModel) {
    var typed by remember(p) { mutableStateOf("") }
    val canConfirm = !p.typed || typed == p.pkg
    AlertDialog(
        onDismissRequest = vm::cancelPending,
        confirmButton = { TextButton(onClick = vm::confirmPending, enabled = canConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = vm::cancelPending) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(opTitle(p.command))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${p.label}\n${p.pkg}")
                Text(stringResource(R.string.command_label, p.command.render()), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(opBody(p.command)))
                if (p.isSystem) Text(stringResource(R.string.system_warning), color = MaterialTheme.colorScheme.error)
                if (p.typed) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(stringResource(R.string.type_to_confirm)) })
                }
            }
        },
    )
}

// ---------- shared helpers ----------
internal fun formatTime(ms: Long): String = DateFormat.getDateTimeInstance().format(Date(ms))

internal fun opTitle(c: ShellCommand): Int = when (c) {
    is ShellCommand.DisableUser -> R.string.confirm_disable_title
    is ShellCommand.Enable -> R.string.confirm_enable_title
    is ShellCommand.ForceStop -> R.string.confirm_forcestop_title
    is ShellCommand.UninstallForUser -> R.string.confirm_uninstall_title
    is ShellCommand.InstallExisting -> R.string.confirm_restore_title
    is ShellCommand.GrantPermission -> R.string.confirm_grant_title
    is ShellCommand.RevokePermission -> R.string.confirm_revoke_title
    is ShellCommand.AppOpsSet -> R.string.confirm_appop_title
    else -> R.string.confirm
}

internal fun opBody(c: ShellCommand): Int = when (c) {
    is ShellCommand.DisableUser -> R.string.confirm_disable_body
    is ShellCommand.Enable -> R.string.confirm_enable_body
    is ShellCommand.ForceStop -> R.string.confirm_forcestop_body
    is ShellCommand.UninstallForUser -> R.string.confirm_uninstall_body
    is ShellCommand.InstallExisting -> R.string.confirm_restore_body
    is ShellCommand.GrantPermission -> R.string.confirm_grant_body
    is ShellCommand.RevokePermission -> R.string.confirm_revoke_body
    is ShellCommand.AppOpsSet -> R.string.confirm_appop_body
    else -> R.string.confirm
}

internal fun outcomeLabel(o: Outcome): Int = when (o) {
    Outcome.SUCCEEDED -> R.string.outcome_succeeded
    Outcome.FAILED -> R.string.outcome_failed
    Outcome.REJECTED -> R.string.outcome_rejected
    Outcome.TIMED_OUT -> R.string.outcome_timeout
    Outcome.CANCELLED -> R.string.outcome_cancelled
    Outcome.UNSUPPORTED -> R.string.outcome_unsupported
}

internal fun resultTitle(r: OperationResult): Int = when {
    r.isConfirmedSuccess -> R.string.result_confirmed
    r.outcome == Outcome.SUCCEEDED -> R.string.result_unverified
    r.outcome == Outcome.REJECTED -> R.string.result_rejected
    r.outcome == Outcome.TIMED_OUT -> R.string.result_timeout
    else -> R.string.result_failed
}

internal fun rollbackText(r: RollbackAvailability): Int = when (r) {
    RollbackAvailability.AVAILABLE -> R.string.rollback_available
    RollbackAvailability.PARTIAL -> R.string.rollback_partial
    RollbackAvailability.UNAVAILABLE -> R.string.rollback_unavailable
}

internal fun adbStateName(s: AdbState): Int = when (s) {
    AdbState.NOT_CONFIGURED -> R.string.adb_state_not_configured
    AdbState.AWAITING_PAIRING -> R.string.adb_state_awaiting
    AdbState.PAIRING -> R.string.adb_state_pairing
    AdbState.CONNECTING -> R.string.adb_state_connecting
    AdbState.CONNECTED -> R.string.adb_state_connected
    AdbState.DISCONNECTED -> R.string.adb_state_disconnected
    AdbState.FAILED -> R.string.adb_state_failed
}

private fun capName(id: CapabilityId): Int = when (id) {
    CapabilityId.LIST_PACKAGES -> R.string.cap_list_packages
    CapabilityId.OPEN_APP_INFO -> R.string.cap_open_app_info
    CapabilityId.ADB_WIRELESS_PAIRING -> R.string.cap_adb_pairing
    CapabilityId.FORCE_STOP -> R.string.cap_force_stop
    CapabilityId.DISABLE_PACKAGE -> R.string.cap_disable
    CapabilityId.UNINSTALL_FOR_USER -> R.string.cap_uninstall_user
    CapabilityId.GRANT_REVOKE_PERMISSION -> R.string.cap_grant_revoke
    CapabilityId.READ_APPOPS -> R.string.cap_appops
    CapabilityId.TERMINAL -> R.string.cap_terminal
    CapabilityId.READ_LOGCAT -> R.string.cap_logcat
    CapabilityId.READ_PERMISSIONS -> R.string.cap_read_permissions
    CapabilityId.WRITE_APPOPS -> R.string.cap_write_appops
    CapabilityId.INSTALL_EXISTING -> R.string.cap_install_existing
    CapabilityId.DIAGNOSTICS -> R.string.cap_diagnostics
    CapabilityId.SYSTEM_UNINSTALL_DIALOG -> R.string.cap_system_uninstall
    CapabilityId.EXTENSIONS -> R.string.cap_extensions
}

private fun statusLabel(s: CapabilityStatus): Int = when (s) {
    CapabilityStatus.Supported -> R.string.status_supported
    is CapabilityStatus.PermissionRequired -> R.string.status_permission
    is CapabilityStatus.HigherPrivilegeRequired -> R.string.status_higher
    is CapabilityStatus.UnsupportedOnDevice -> R.string.status_unsupported
    is CapabilityStatus.TemporarilyUnavailable -> R.string.status_unavailable
    is CapabilityStatus.Failed -> R.string.status_failed
}

private fun reasonOf(s: CapabilityStatus): ReasonCode? = when (s) {
    CapabilityStatus.Supported -> null
    is CapabilityStatus.PermissionRequired -> s.reason
    is CapabilityStatus.HigherPrivilegeRequired -> s.reason
    is CapabilityStatus.UnsupportedOnDevice -> s.reason
    is CapabilityStatus.TemporarilyUnavailable -> s.reason
    is CapabilityStatus.Failed -> s.reason
}

private fun reasonText(r: ReasonCode): Int = when (r) {
    ReasonCode.NOT_IMPLEMENTED_IN_BUILD -> R.string.reason_not_implemented
    ReasonCode.REQUIRES_ADB_CONNECTION -> R.string.reason_requires_adb
    ReasonCode.REQUIRES_API_30 -> R.string.reason_api30
}

private fun filterName(f: AppFilter): Int = when (f) {
    AppFilter.ALL -> R.string.filter_all
    AppFilter.USER -> R.string.filter_user
    AppFilter.SYSTEM -> R.string.filter_system
    AppFilter.DISABLED -> R.string.filter_disabled
    AppFilter.REMOVED -> R.string.filter_removed
}
