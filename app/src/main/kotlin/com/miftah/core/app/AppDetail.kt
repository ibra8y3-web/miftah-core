package com.miftah.core.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miftah.core.execution.AdbState
import com.miftah.core.execution.KnownAppOps
import com.miftah.core.model.AppInfo
import com.miftah.core.model.ProtectionClass

@Composable
internal fun AppDetailDialog(app: AppInfo, s: UiState, vm: MainViewModel, actions: Actions) {
    val connected = s.adbState == AdbState.CONNECTED
    val canManage = connected && app.packageName != s.ownPackage
    val allowed = app.packageName in s.settings.allowlist
    AlertDialog(
        onDismissRequest = { vm.selectApp(null) },
        confirmButton = { TextButton(onClick = { vm.selectApp(null) }) { Text(stringResource(R.string.close)) } },
        title = { Text(app.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                Text("${app.versionName ?: "-"} (${app.versionCode})")
                Text(stringResource(if (app.isSystem) R.string.kind_system else R.string.kind_user))
                Text(stringResource(when { !app.installed -> R.string.state_removed; app.enabled -> R.string.state_enabled; else -> R.string.state_disabled }))
                if (allowed) Text(stringResource(R.string.allowlisted_note), style = MaterialTheme.typography.bodySmall)

                if (app.installed) TextButton(onClick = { actions.openAppInfo(app.packageName) }) { Text(stringResource(R.string.open_settings)) }
                if (app.installed && !app.isSystem && app.packageName != s.ownPackage) {
                    TextButton(onClick = { actions.uninstallDialog(app.packageName) }) { Text(stringResource(R.string.action_uninstall_dialog)) }
                }
                TextButton(onClick = { vm.toggleAllow(app.packageName) }) {
                    Text(stringResource(if (allowed) R.string.allowlist_remove else R.string.allowlist_add))
                }

                if (canManage) {
                    if (!app.installed) {
                        TextButton(onClick = { vm.askRestore(app) }) { Text(stringResource(R.string.action_restore)) }
                    } else {
                        if (app.enabled) TextButton(onClick = { vm.askDisable(app) }) { Text(stringResource(R.string.action_disable)) }
                        else TextButton(onClick = { vm.askEnable(app) }) { Text(stringResource(R.string.action_enable)) }
                        TextButton(onClick = { vm.askForceStop(app) }) { Text(stringResource(R.string.action_force_stop)) }
                        TextButton(onClick = { vm.askUninstall(app) }) { Text(stringResource(R.string.action_uninstall_user)) }
                    }
                } else {
                    Text(stringResource(R.string.adb_required), style = MaterialTheme.typography.bodySmall)
                }

                if (app.installed) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.perms_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.perms_scope), style = MaterialTheme.typography.bodySmall)
                    val pv = s.perm?.takeIf { it.pkg == app.packageName }
                    when {
                        pv == null -> Text(stringResource(R.string.working))
                        pv.error != null -> Text(pv.error, color = MaterialTheme.colorScheme.error)
                        pv.entries.isNullOrEmpty() -> Text(stringResource(R.string.perms_none))
                        else -> pv.entries.forEach { p ->
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text(p.name, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${stringResource(protectionName(p.protection))} • ${stringResource(if (p.granted) R.string.perm_granted else R.string.perm_denied)}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (canManage && p.protection == ProtectionClass.RUNTIME) {
                                    if (p.granted) TextButton(onClick = { vm.askRevoke(app, p.name) }) { Text(stringResource(R.string.perm_revoke_btn)) }
                                    else TextButton(onClick = { vm.askGrant(app, p.name) }) { Text(stringResource(R.string.perm_grant_btn)) }
                                } else if (p.protection == ProtectionClass.SIGNATURE) {
                                    Text(stringResource(R.string.perm_signature_note), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.appops_title), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { vm.readAppOps(app) }, enabled = canManage) { Text(stringResource(R.string.appops_read_btn)) }
                    val ov = s.appOps?.takeIf { it.pkg == app.packageName }
                    if (ov != null) {
                        if (ov.loading) Text(stringResource(R.string.working))
                        ov.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        KnownAppOps.forEach { op ->
                            if (ov.modes.containsKey(op)) {
                                val mode = ov.modes[op]
                                Text("$op: ${mode ?: stringResource(R.string.appop_unreadable)}", style = MaterialTheme.typography.bodySmall)
                                if (canManage && mode != null) {
                                    Row {
                                        TextButton(onClick = { vm.askAppOp(app, op, "allow") }, enabled = mode != "allow") { Text(stringResource(R.string.appop_allow)) }
                                        TextButton(onClick = { vm.askAppOp(app, op, "ignore") }, enabled = mode != "ignore") { Text(stringResource(R.string.appop_ignore)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun protectionName(p: ProtectionClass): Int = when (p) {
    ProtectionClass.NORMAL -> R.string.perm_normal
    ProtectionClass.RUNTIME -> R.string.perm_runtime
    ProtectionClass.SIGNATURE -> R.string.perm_signature
    ProtectionClass.UNKNOWN -> R.string.perm_unknown
}
