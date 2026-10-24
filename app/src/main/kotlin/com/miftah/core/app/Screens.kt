package com.miftah.core.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miftah.core.execution.AdbState
import com.miftah.core.model.*

@Composable
fun MiftahApp(vm: MainViewModel, onOpenAppInfo: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf(R.string.tab_dashboard, R.string.tab_adb, R.string.tab_capabilities, R.string.tab_apps)
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) }) }
                }
                when {
                    tab == 1 -> AdbScreen(state, vm)
                    state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> ErrorView(state.error!!, vm::refresh)
                    tab == 0 -> Dashboard(state)
                    tab == 2 -> CapabilityList(state.capabilities)
                    else -> AppList(state, vm::setQuery, vm::setFilter, vm::selectApp)
                }
            }
            Dialogs(state, vm, onOpenAppInfo)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.error_load), style = MaterialTheme.typography.titleMedium)
        Text(message, Modifier.padding(vertical = 8.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
            Text(stringResource(R.string.apps_found, s.apps.size, s.apps.count { !it.isSystem }, s.apps.count { it.isSystem }))
        }
        item {
            val c = s.capabilities
            Text(stringResource(R.string.cap_summary, c.count { it.status is CapabilityStatus.Supported }, c.size))
        }
    }
}

@Composable
private fun AdbScreen(s: UiState, vm: MainViewModel) {
    var pairPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    val inProgress = s.adbState == AdbState.PAIRING || s.adbState == AdbState.CONNECTING
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.adb_state_label, stringResource(adbStateName(s.adbState))), style = MaterialTheme.typography.titleMedium)
        s.adbIdentity?.let { Text(stringResource(R.string.adb_shell_identity, it.name, it.uid)) }
        s.adbError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.adb_steps), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.adb_risk), style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(pairPort, { pairPort = it.filter(Char::isDigit) }, singleLine = true,
            label = { Text(stringResource(R.string.adb_pair_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
            label = { Text(stringResource(R.string.adb_pair_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.pair(pairPort, code); code = "" }, enabled = !inProgress) {
            Text(stringResource(R.string.adb_pair_btn))
        }

        OutlinedTextField(connectPort, { connectPort = it.filter(Char::isDigit) }, singleLine = true,
            label = { Text(stringResource(R.string.adb_connect_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { vm.connect(connectPort) },
            enabled = !inProgress && (s.adbState == AdbState.DISCONNECTED || s.adbState == AdbState.FAILED),
        ) { Text(stringResource(R.string.adb_connect_btn)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::disconnect, enabled = s.adbState == AdbState.CONNECTED) {
                Text(stringResource(R.string.adb_disconnect_btn))
            }
            OutlinedButton(onClick = vm::clearPairing, enabled = !inProgress && s.adbState != AdbState.NOT_CONFIGURED) {
                Text(stringResource(R.string.adb_clear_btn))
            }
        }
        if (inProgress) CircularProgressIndicator()
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
private fun AppList(s: UiState, onQuery: (String) -> Unit, onFilter: (AppFilter) -> Unit, onSelect: (AppInfo) -> Unit) {
    val shown = remember(s.apps, s.query, s.filter) { AppQuery.apply(s.apps, s.query, s.filter) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = s.query, onValueChange = onQuery, singleLine = true,
            label = { Text(stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilter.entries.forEach { f ->
                FilterChip(selected = s.filter == f, onClick = { onFilter(f) }, label = { Text(stringResource(filterName(f))) })
            }
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.no_results), Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(shown, key = { it.packageName }) { a ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(a) }.padding(vertical = 8.dp)) {
                        Text(a.label, style = MaterialTheme.typography.titleSmall)
                        Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                        Text("${a.versionName ?: "-"} (${a.versionCode})", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Dialogs(s: UiState, vm: MainViewModel, onOpenAppInfo: (String) -> Unit) {
    if (s.busy) {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(stringResource(R.string.working)) },
            text = { CircularProgressIndicator() },
        )
    }
    s.lastResult?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            confirmButton = { TextButton(onClick = vm::dismissResult) { Text(stringResource(R.string.close)) } },
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
    s.pending?.let { p ->
        val isDisable = p.action == AppAction.DISABLE
        AlertDialog(
            onDismissRequest = vm::cancelPending,
            confirmButton = { TextButton(onClick = vm::confirmPending) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = vm::cancelPending) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(if (isDisable) R.string.confirm_disable_title else R.string.confirm_enable_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.app.label}\n${p.app.packageName}")
                    Text(stringResource(if (isDisable) R.string.confirm_disable_body else R.string.confirm_enable_body))
                    if (p.app.isSystem) Text(stringResource(R.string.system_warning), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    val app = s.selectedApp
    if (app != null && s.pending == null && s.lastResult == null && !s.busy) {
        val canManage = s.adbState == AdbState.CONNECTED && app.packageName != s.ownPackage
        AlertDialog(
            onDismissRequest = { vm.selectApp(null) },
            confirmButton = { TextButton(onClick = { vm.selectApp(null) }) { Text(stringResource(R.string.close)) } },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text("${app.versionName ?: "-"} (${app.versionCode})")
                    Text(stringResource(if (app.isSystem) R.string.kind_system else R.string.kind_user))
                    Text(stringResource(if (app.enabled) R.string.state_enabled else R.string.state_disabled))
                    TextButton(onClick = { onOpenAppInfo(app.packageName) }) { Text(stringResource(R.string.open_settings)) }
                    if (canManage) {
                        if (app.enabled) {
                            TextButton(onClick = { vm.askConfirm(app, AppAction.DISABLE) }) { Text(stringResource(R.string.action_disable)) }
                        } else {
                            TextButton(onClick = { vm.askConfirm(app, AppAction.ENABLE) }) { Text(stringResource(R.string.action_enable)) }
                        }
                    } else {
                        Text(stringResource(R.string.adb_required), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        )
    }
}

private fun resultTitle(r: OperationResult) = when {
    r.isConfirmedSuccess -> R.string.result_confirmed
    r.outcome == Outcome.SUCCEEDED -> R.string.result_unverified
    r.outcome == Outcome.REJECTED -> R.string.result_rejected
    r.outcome == Outcome.TIMED_OUT -> R.string.result_timeout
    else -> R.string.result_failed
}

private fun rollbackText(r: RollbackAvailability) = when (r) {
    RollbackAvailability.AVAILABLE -> R.string.rollback_available
    RollbackAvailability.PARTIAL -> R.string.rollback_partial
    RollbackAvailability.UNAVAILABLE -> R.string.rollback_unavailable
}

private fun adbStateName(s: AdbState) = when (s) {
    AdbState.NOT_CONFIGURED -> R.string.adb_state_not_configured
    AdbState.AWAITING_PAIRING -> R.string.adb_state_awaiting
    AdbState.PAIRING -> R.string.adb_state_pairing
    AdbState.CONNECTING -> R.string.adb_state_connecting
    AdbState.CONNECTED -> R.string.adb_state_connected
    AdbState.DISCONNECTED -> R.string.adb_state_disconnected
    AdbState.FAILED -> R.string.adb_state_failed
}

private fun capName(id: CapabilityId) = when (id) {
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
}

private fun statusLabel(s: CapabilityStatus) = when (s) {
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

private fun reasonText(r: ReasonCode) = when (r) {
    ReasonCode.NOT_IMPLEMENTED_IN_BUILD -> R.string.reason_not_implemented
    ReasonCode.REQUIRES_ADB_CONNECTION -> R.string.reason_requires_adb
    ReasonCode.REQUIRES_API_30 -> R.string.reason_api30
}

private fun filterName(f: AppFilter) = when (f) {
    AppFilter.ALL -> R.string.filter_all
    AppFilter.USER -> R.string.filter_user
    AppFilter.SYSTEM -> R.string.filter_system
    AppFilter.DISABLED -> R.string.filter_disabled
}
