package com.miftah.core.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    val requestNotifications: () -> Unit,
)

private enum class Dest(val label: Int, val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    APPS(R.string.tab_apps, R.drawable.ic_apps),
    ADB(R.string.tab_adb, R.drawable.ic_link),
    TOOLS(R.string.nav_tools, R.drawable.ic_tools),
    MORE(R.string.nav_more, R.drawable.ic_more),
}

@Composable
fun MiftahApp(vm: MainViewModel, actions: Actions) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
    var toolsTab by rememberSaveable { mutableIntStateOf(0) }
    var moreTab by rememberSaveable { mutableIntStateOf(0) }
    val dark = when (state.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MiftahTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    Dest.entries.forEach { d ->
                        NavigationBarItem(
                            selected = dest == d,
                            onClick = { dest = d },
                            icon = { Icon(painterResource(d.icon), contentDescription = null) },
                            label = { Text(stringResource(d.label), maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
                AnimatedContent(
                    targetState = dest,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "destination",
                ) { d ->
                    when (d) {
                        Dest.HOME -> Gated(state, vm::refresh) { Dashboard(state) { dest = Dest.ADB } }
                        Dest.APPS -> Gated(state, vm::refresh) { AppList(state, vm) }
                        Dest.ADB -> AdbScreen(state, vm, actions)
                        Dest.TOOLS -> Tabbed(
                            listOf(R.string.tab_plans, R.string.tab_terminal, R.string.tab_diag, R.string.tab_audit),
                            toolsTab, { toolsTab = it },
                        ) { i ->
                            when (i) {
                                0 -> PlansScreen(state, vm)
                                1 -> TerminalScreen(state, vm)
                                2 -> DiagnosticsScreen(state, vm, actions)
                                else -> AuditScreen(state, vm, actions)
                            }
                        }
                        Dest.MORE -> Tabbed(
                            listOf(R.string.tab_capabilities, R.string.tab_settings, R.string.tab_extensions),
                            moreTab, { moreTab = it },
                        ) { i ->
                            when (i) {
                                0 -> CapabilityList(state.capabilities)
                                1 -> SettingsScreen(state, vm, actions)
                                else -> ExtensionsScreen()
                            }
                        }
                    }
                }
            }
        }
        Dialogs(state, vm, actions)
    }
}

@Composable
private fun Tabbed(titles: List<Int>, selected: Int, onSelect: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected, containerColor = Color.Transparent) {
            titles.forEachIndexed { i, t ->
                Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(stringResource(t), maxLines = 1) })
            }
        }
        Box(Modifier.weight(1f)) { content(selected) }
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
private fun Dashboard(s: UiState, onOpenAdb: () -> Unit) {
    val d = s.device
    val installed = s.apps.filter { it.installed }
    val connected = s.adbState == AdbState.CONNECTED
    ScreenColumn {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF6A3DF0), Color(0xFF24105F)))).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(painterResource(R.drawable.ic_key), contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text(
                                stringResource(if (s.level == AccessLevel.ADB) R.string.access_level_adb else R.string.access_level_standard),
                                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    StatusPill(
                        stringResource(R.string.adb_state_label, stringResource(adbStateName(s.adbState))),
                        tone = when (s.adbState) { AdbState.CONNECTED -> Tone.OK; AdbState.FAILED -> Tone.ERROR; else -> Tone.NEUTRAL },
                        iconRes = if (connected) R.drawable.ic_check else R.drawable.ic_link,
                    )
                    if (!connected) {
                        Button(
                            onClick = onOpenAdb,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3B1AA6)),
                        ) { Text(stringResource(R.string.cta_connect)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(installed.size.toString(), stringResource(R.string.stat_apps), Modifier.weight(1f))
            StatCard(installed.count { !it.enabled }.toString(), stringResource(R.string.stat_disabled), Modifier.weight(1f))
            StatCard(s.capabilities.count { it.status is CapabilityStatus.Supported }.toString(), stringResource(R.string.stat_caps), Modifier.weight(1f))
        }
        if (d != null) {
            SectionCard(title = stringResource(R.string.device_title)) {
                Text("${d.manufacturer} ${d.model}", style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.android_version, d.release, d.sdkInt))
                Text(stringResource(R.string.abis, d.abis.joinToString()), style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard(title = stringResource(R.string.recent_ops)) {
            if (s.audit.entries.isEmpty()) {
                Text(stringResource(R.string.no_ops))
            } else {
                s.audit.entries.take(3).forEach { e ->
                    Text("${e.result.name} • ${e.target} • ${stringResource(outcomeLabel(e.result.outcome))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CapabilityList(list: List<Capability>) {
    ScreenColumn {
        list.forEach { c ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(capName(c.id)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    val supported = c.status is CapabilityStatus.Supported
                    StatusPill(
                        stringResource(statusLabel(c.status)), capTone(c.status),
                        if (supported) R.drawable.ic_check else R.drawable.ic_warning,
                    )
                }
                reasonOf(c.status)?.let { Text(stringResource(reasonText(it)), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun capTone(s: CapabilityStatus): Tone = when (s) {
    CapabilityStatus.Supported -> Tone.OK
    is CapabilityStatus.HigherPrivilegeRequired, is CapabilityStatus.PermissionRequired -> Tone.WARN
    is CapabilityStatus.UnsupportedOnDevice, is CapabilityStatus.Failed -> Tone.ERROR
    is CapabilityStatus.TemporarilyUnavailable -> Tone.NEUTRAL
}

@Composable
private fun AppList(s: UiState, vm: MainViewModel) {
    val shown = remember(s.apps, s.query, s.filter) { AppQuery.apply(s.apps, s.query, s.filter) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = s.query, onValueChange = vm::setQuery, singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            label = { Text(stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilter.entries.forEach { f ->
                FilterChip(selected = s.filter == f, onClick = { vm.setFilter(f) }, label = { Text(stringResource(filterName(f))) })
            }
        }
        if (s.selection.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plan_selected, s.selection.size), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = vm::clearSelection) { Text(stringResource(R.string.plan_clear_btn)) }
            }
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.no_results), Modifier.padding(24.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(shown, key = { it.packageName }) { a ->
                    AppRow(a, a.packageName in s.selection, onClick = { vm.selectApp(a) }, onToggle = { vm.toggleSelect(a.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AppRow(a: AppInfo, selected: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    val system = stringResource(R.string.kind_system)
    val removed = stringResource(R.string.state_removed)
    val disabled = stringResource(R.string.state_disabled)
    val tags = listOfNotNull(
        a.versionName ?: "-",
        if (a.isSystem) system else null,
        if (!a.installed) removed else if (!a.enabled) disabled else null,
    ).joinToString(" • ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(a.packageName, a.label)
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
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
