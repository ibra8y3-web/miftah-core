package com.miftah.core.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miftah.core.model.*

@Composable
fun MiftahApp(vm: MainViewModel, onOpenAppInfo: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf(R.string.tab_dashboard, R.string.tab_capabilities, R.string.tab_apps)
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                TabRow(selectedTabIndex = tab) {
                    titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) }) }
                }
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> ErrorView(state.error!!, vm::refresh)
                    tab == 0 -> Dashboard(state)
                    tab == 1 -> CapabilityList(state.capabilities)
                    else -> AppList(state, vm::setQuery, vm::setFilter, onOpenAppInfo)
                }
            }
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
        item { Text(stringResource(R.string.access_level_standard), style = MaterialTheme.typography.titleMedium) }
        item { Text(stringResource(R.string.adb_not_connected)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppList(s: UiState, onQuery: (String) -> Unit, onFilter: (AppFilter) -> Unit, onOpen: (String) -> Unit) {
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
                    Column(Modifier.fillMaxWidth().clickable { onOpen(a.packageName) }.padding(vertical = 8.dp)) {
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
