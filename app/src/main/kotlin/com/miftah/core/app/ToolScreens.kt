package com.miftah.core.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miftah.core.execution.AdbState
import com.miftah.core.model.*
import kotlin.math.roundToInt

internal enum class Tool(val title: Int, val desc: Int, val icon: Int) {
    PRIVACY(R.string.tool_privacy, R.string.tool_privacy_desc, R.drawable.ic_eye),
    SHIELD(R.string.tool_shield, R.string.tool_shield_desc, R.drawable.ic_shield),
    STARTUP(R.string.tool_startup, R.string.tool_startup_desc, R.drawable.ic_power),
    DISPLAY(R.string.tool_display, R.string.tool_display_desc, R.drawable.ic_display),
    PLANS(R.string.tab_plans, R.string.tool_plans_desc, R.drawable.ic_plan),
    TERMINAL(R.string.tab_terminal, R.string.tool_terminal_desc, R.drawable.ic_terminal),
    DIAG(R.string.tab_diag, R.string.tool_diag_desc, R.drawable.ic_heart),
    AUDIT(R.string.tab_audit, R.string.tool_audit_desc, R.drawable.ic_history),
}

@Composable
internal fun ToolsHost(s: UiState, vm: MainViewModel, actions: Actions, tool: Tool?, onTool: (Tool?) -> Unit) {
    BackHandler(enabled = tool != null) { onTool(null) }
    if (tool == null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(Tool.entries) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onTool(t) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(t.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(t.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onTool(null) }) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(tool.title), style = MaterialTheme.typography.titleLarge)
            }
            Box(Modifier.weight(1f)) {
                when (tool) {
                    Tool.PRIVACY -> PrivacyScreen(s, vm)
                    Tool.SHIELD -> ShieldScreen(s, vm, actions)
                    Tool.STARTUP -> StartupScreen(s, vm)
                    Tool.DISPLAY -> DisplayScreen(s, vm)
                    Tool.PLANS -> PlansScreen(s, vm)
                    Tool.TERMINAL -> TerminalScreen(s, vm)
                    Tool.DIAG -> DiagnosticsScreen(s, vm, actions)
                    Tool.AUDIT -> AuditScreen(s, vm, actions)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Privacy doctor
private fun categoryName(c: PrivacyCategory): Int = when (c) {
    PrivacyCategory.ACCESSIBILITY -> R.string.cat_accessibility
    PrivacyCategory.NOTIFICATION_LISTENER -> R.string.cat_notification
    PrivacyCategory.DEVICE_ADMIN -> R.string.cat_admin
    PrivacyCategory.OVERLAY -> R.string.cat_overlay
    PrivacyCategory.USAGE_ACCESS -> R.string.cat_usage
    PrivacyCategory.INSTALL_UNKNOWN -> R.string.cat_install
}

private fun categoryHelp(c: PrivacyCategory): Int = when (c) {
    PrivacyCategory.ACCESSIBILITY -> R.string.cat_accessibility_help
    PrivacyCategory.NOTIFICATION_LISTENER -> R.string.cat_notification_help
    PrivacyCategory.DEVICE_ADMIN -> R.string.cat_admin_help
    PrivacyCategory.OVERLAY -> R.string.cat_overlay_help
    PrivacyCategory.USAGE_ACCESS -> R.string.cat_usage_help
    PrivacyCategory.INSTALL_UNKNOWN -> R.string.cat_install_help
}

@Composable
internal fun PrivacyScreen(s: UiState, vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.scanPrivacy() }
    val ui = s.privacy
    val report = ui.report
    val byPkg = remember(s.apps) { s.apps.associateBy { it.packageName } }
    ScreenColumn {
        Button(onClick = vm::scanPrivacy, enabled = !ui.loading) { Text(stringResource(R.string.privacy_rescan)) }
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (report != null) {
            val score = PrivacyScore.score(report.findings)
            val level = PrivacyScore.level(score)
            SectionCard(title = stringResource(R.string.privacy_summary)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(
                        stringResource(when (level) { RiskLevel.LOW -> R.string.privacy_level_low; RiskLevel.MEDIUM -> R.string.privacy_level_medium; RiskLevel.HIGH -> R.string.privacy_level_high }),
                        when (level) { RiskLevel.LOW -> Tone.OK; RiskLevel.MEDIUM -> Tone.WARN; RiskLevel.HIGH -> Tone.ERROR },
                        if (level == RiskLevel.LOW) R.drawable.ic_check else R.drawable.ic_warning,
                    )
                    Text(stringResource(R.string.privacy_points, score), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.privacy_formula), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.privacy_scope, report.scannedPackages, report.unreadable), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.tap_to_manage), style = MaterialTheme.typography.bodySmall)
            }
            PrivacyCategory.entries.forEach { cat ->
                val items = report.findings.filter { it.category == cat }.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
                SectionCard(title = "${stringResource(categoryName(cat))} (${items.size})") {
                    Text(stringResource(categoryHelp(cat)), style = MaterialTheme.typography.bodySmall)
                    if (items.isEmpty()) Text(stringResource(R.string.privacy_none))
                    items.take(12).forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { byPkg[f.pkg]?.let { vm.selectApp(it) } }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIcon(f.pkg, f.label, 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(f.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (f.isSystem) Text(stringResource(R.string.kind_system), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (items.size > 12) Text(stringResource(R.string.privacy_more, items.size - 12), style = MaterialTheme.typography.labelMedium)
                }
            }
            SectionCard(title = stringResource(R.string.privacy_sensitive_title)) {
                Text(stringResource(R.string.privacy_sensitive_note), style = MaterialTheme.typography.bodySmall)
                if (report.sensitiveGrants.isEmpty()) Text(stringResource(R.string.privacy_none))
                report.sensitiveGrants.forEach { g ->
                    Text("${g.permission.removePrefix("android.permission.")} (${g.packages.size})", style = MaterialTheme.typography.titleSmall)
                    Text(
                        g.packages.take(8).joinToString(", ") { byPkg[it]?.label ?: it } + if (g.packages.size > 8) " …" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Startup manager
@Composable
internal fun StartupScreen(s: UiState, vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.loadStartup() }
    val connected = s.adbState == AdbState.CONNECTED
    val ui = s.startup
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.startup_note), style = MaterialTheme.typography.bodySmall)
        if (!connected) Text(stringResource(R.string.adb_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = vm::loadStartup, enabled = !ui.loading) { Text(stringResource(R.string.diag_refresh)) }
            if (ui.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!ui.loading && ui.entries.isEmpty() && ui.error == null) Text(stringResource(R.string.startup_empty))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(ui.entries, key = { it.pkg }) { entry ->
                var open by rememberSaveable(entry.pkg) { mutableStateOf(false) }
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = !open },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(entry.pkg, entry.label, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.startup_components, entry.components.size), style = MaterialTheme.typography.labelMedium)
                        }
                        if (entry.isSystem) Text(stringResource(R.string.kind_system), style = MaterialTheme.typography.labelSmall)
                    }
                    if (open) {
                        Text(stringResource(R.string.startup_risk), style = MaterialTheme.typography.bodySmall)
                        entry.components.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.className.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(c.className, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Switch(checked = c.enabled, onCheckedChange = { vm.askToggleComponent(entry, c.className, it) }, enabled = connected)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Shield (ad blocker + firewall)
@Composable
private fun PackageListCard(
    title: String, pkgs: Set<String>, apps: Map<String, AppInfo>, emptyText: String,
    addLabel: String, addEnabled: Boolean, onAdd: () -> Unit, onRemove: (String) -> Unit,
) {
    SectionCard(title = title) {
        if (pkgs.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall)
        pkgs.sorted().forEach { pkg ->
            val label = apps[pkg]?.label ?: pkg
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(pkg, label, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { onRemove(pkg) }) { Text(stringResource(R.string.shield_remove)) }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = addEnabled) { Text(addLabel) }
    }
}

@Composable
internal fun ShieldScreen(s: UiState, vm: MainViewModel, actions: Actions) {
    LaunchedEffect(Unit) { vm.refreshShield() }
    var mode by rememberSaveable { mutableStateOf(VpnMode.ADBLOCK) }
    var domain by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val st = s.settings
    val vpn = s.vpn
    val sh = s.shield
    val byPkg = remember(s.apps) { s.apps.associateBy { it.packageName } }
    val cfg = VpnConfig(mode, st.shieldExcluded.toList(), st.firewallBlocked.toList(), st.shieldUpstream)
    ScreenColumn {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    stringResource(if (vpn.running) R.string.shield_status_running else R.string.shield_status_stopped),
                    if (vpn.running) Tone.OK else Tone.NEUTRAL,
                    if (vpn.running) R.drawable.ic_check else R.drawable.ic_shield,
                )
            }
            if (vpn.running) {
                Text(
                    stringResource(if (vpn.mode == VpnMode.FIREWALL) R.string.shield_mode_firewall else R.string.shield_mode_adblock),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (vpn.mode == VpnMode.ADBLOCK) Text(stringResource(R.string.shield_stats, vpn.total, vpn.blocked))
            }
            vpn.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            sh.systemVpn?.let { Text(stringResource(if (it) R.string.shield_system_vpn_on else R.string.shield_system_vpn_off), style = MaterialTheme.typography.bodySmall) }
            if (sh.privateDns == "hostname") Text(stringResource(R.string.shield_private_dns_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.shield_single_vpn_note), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == VpnMode.ADBLOCK, onClick = { mode = VpnMode.ADBLOCK }, label = { Text(stringResource(R.string.shield_mode_adblock)) })
            FilterChip(selected = mode == VpnMode.FIREWALL, onClick = { mode = VpnMode.FIREWALL }, label = { Text(stringResource(R.string.shield_mode_firewall)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.startVpn(cfg) },
                enabled = mode == VpnMode.ADBLOCK || st.firewallBlocked.isNotEmpty(),
            ) { Text(stringResource(if (vpn.running) R.string.shield_restart else R.string.shield_start)) }
            if (vpn.running) OutlinedButton(onClick = actions.stopVpn) { Text(stringResource(R.string.shield_stop)) }
        }

        if (mode == VpnMode.ADBLOCK) {
            SectionCard(title = stringResource(R.string.shield_mode_adblock)) {
                Text(stringResource(R.string.shield_adblock_desc), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.shield_dns_note), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(title = stringResource(R.string.shield_upstream_title)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Upstream.entries.forEach { u ->
                        FilterChip(selected = st.shieldUpstream == u, onClick = { vm.setUpstream(u) }, label = {
                            Text(stringResource(when (u) {
                                Upstream.SYSTEM -> R.string.upstream_system
                                Upstream.CLOUDFLARE -> R.string.upstream_cloudflare
                                Upstream.GOOGLE -> R.string.upstream_google
                                Upstream.QUAD9 -> R.string.upstream_quad9
                            }))
                        })
                    }
                }
            }
            SectionCard(title = stringResource(R.string.shield_lists_title)) {
                Text(stringResource(R.string.shield_counts, sh.counts.builtIn, sh.counts.imported, sh.counts.custom, sh.counts.allowed), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(domain, { domain = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shield_domain_hint)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.addDomain(domain, allow = false); domain = "" }, enabled = domain.isNotBlank()) { Text(stringResource(R.string.shield_block_btn)) }
                    OutlinedButton(onClick = { vm.addDomain(domain, allow = true); domain = "" }, enabled = domain.isNotBlank()) { Text(stringResource(R.string.shield_allow_btn)) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.clearList(BlocklistFiles.CUSTOM) }, enabled = sh.counts.custom > 0) { Text(stringResource(R.string.shield_clear_custom)) }
                    TextButton(onClick = { vm.clearList(BlocklistFiles.ALLOW) }, enabled = sh.counts.allowed > 0) { Text(stringResource(R.string.shield_clear_allow)) }
                    TextButton(onClick = { vm.clearList(BlocklistFiles.IMPORTED) }, enabled = sh.counts.imported > 0) { Text(stringResource(R.string.shield_clear_imported)) }
                }
                sh.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            SectionCard(title = stringResource(R.string.shield_import_title)) {
                Text(stringResource(R.string.shield_import_note), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shield_import_hint)) })
                Button(onClick = { vm.importBlocklist(url) }, enabled = url.isNotBlank() && !sh.importing) { Text(stringResource(R.string.shield_import_btn)) }
                if (sh.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            PackageListCard(
                title = stringResource(R.string.shield_excluded_title), pkgs = st.shieldExcluded, apps = byPkg,
                emptyText = stringResource(R.string.shield_list_empty),
                addLabel = stringResource(R.string.shield_add_selected, s.selection.size), addEnabled = s.selection.isNotEmpty(),
                onAdd = vm::addSelectionToExcluded, onRemove = vm::toggleExcluded,
            )
            SectionCard(title = stringResource(R.string.shield_recent_title)) {
                if (vpn.recentBlocked.isEmpty()) Text(stringResource(R.string.shield_recent_none), style = MaterialTheme.typography.bodySmall)
                vpn.recentBlocked.take(15).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        } else {
            SectionCard(title = stringResource(R.string.shield_mode_firewall)) {
                Text(stringResource(R.string.shield_firewall_desc), style = MaterialTheme.typography.bodyMedium)
                if (st.firewallBlocked.isEmpty()) Text(stringResource(R.string.shield_firewall_need_apps), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            PackageListCard(
                title = stringResource(R.string.shield_blocked_title), pkgs = st.firewallBlocked, apps = byPkg,
                emptyText = stringResource(R.string.shield_list_empty),
                addLabel = stringResource(R.string.shield_add_selected, s.selection.size), addEnabled = s.selection.isNotEmpty(),
                onAdd = vm::addSelectionToBlocked, onRemove = vm::toggleBlocked,
            )
        }
    }
}

// ---------------------------------------------------------------- Display tweaks
@Composable
internal fun DisplayScreen(s: UiState, vm: MainViewModel) {
    val connected = s.adbState == AdbState.CONNECTED
    LaunchedEffect(connected) { if (connected) vm.loadDisplay() }
    val d = s.display
    ScreenColumn {
        if (!connected) {
            SectionCard { Text(stringResource(R.string.adb_required), color = MaterialTheme.colorScheme.error) }
        } else {
            SectionCard(title = stringResource(R.string.display_density_title)) {
                val phys = d.physical
                if (phys == null) {
                    Text(stringResource(R.string.display_unreadable), color = MaterialTheme.colorScheme.error)
                } else {
                    val current = d.override ?: phys
                    Text(stringResource(R.string.display_current, current, phys), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.display_density_note), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.8f, 0.9f, 1.1f, 1.2f, 1.3f).forEach { f ->
                            val dpi = ((phys * f) / 5f).roundToInt() * 5
                            FilterChip(
                                selected = d.override == dpi, onClick = { vm.askDensity(dpi) },
                                label = { Text("${(f * 100).roundToInt()}% • $dpi") },
                            )
                        }
                    }
                    OutlinedButton(onClick = { vm.askDensity(null) }, enabled = d.override != null) { Text(stringResource(R.string.display_reset)) }
                }
            }
            SectionCard(title = stringResource(R.string.display_anim_title)) {
                val unreadable = stringResource(R.string.diag_unreadable)
                Text(
                    stringResource(
                        R.string.display_anim_current,
                        d.scales["window_animation_scale"] ?: unreadable,
                        d.scales["transition_animation_scale"] ?: unreadable,
                        d.scales["animator_duration_scale"] ?: unreadable,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("0" to stringResource(R.string.anim_off), "0.5" to "0.5×", "1" to "1×", "1.5" to "1.5×").forEach { (v, label) ->
                        FilterChip(selected = false, onClick = { vm.setAnimationScale(v) }, label = { Text(label) })
                    }
                }
            }
            if (d.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
