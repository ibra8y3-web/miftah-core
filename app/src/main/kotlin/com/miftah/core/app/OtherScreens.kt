package com.miftah.core.app

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.miftah.core.execution.AdbState
import com.miftah.core.execution.StepStatus
import com.miftah.core.execution.StopReason
import com.miftah.core.model.AccessLevel
import com.miftah.core.model.Outcome
import com.miftah.core.model.RollbackAvailability

// ---------------------------------------------------------------- Plans
@Composable
internal fun PlansScreen(s: UiState, vm: MainViewModel) {
    val plan = s.plan
    val connected = s.adbState == AdbState.CONNECTED
    ScreenColumn {
        SectionCard(title = stringResource(R.string.plan_selected, s.selection.size)) {
            if (s.selection.isEmpty()) Text(stringResource(R.string.plan_empty), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanAction.entries.forEach { a ->
                    FilterChip(selected = plan.action == a, onClick = { vm.setPlanAction(a) }, enabled = !plan.running,
                        label = { Text(stringResource(planActionName(a))) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::buildPreview, enabled = s.selection.isNotEmpty() && !plan.running) { Text(stringResource(R.string.plan_preview_btn)) }
                OutlinedButton(onClick = vm::clearSelection, enabled = s.selection.isNotEmpty() && !plan.running) { Text(stringResource(R.string.plan_clear_btn)) }
            }
        }
        plan.preview?.let { p ->
            SectionCard {
                Text(stringResource(R.string.plan_preview_text, p.steps.size, p.systemCount, p.skippedAllowlist, p.skippedNotApplicable))
                Text(stringResource(rollbackText(p.rollback)), style = MaterialTheme.typography.bodySmall)
                if (p.rollback == RollbackAvailability.UNAVAILABLE) Text(stringResource(R.string.plan_no_undo), style = MaterialTheme.typography.bodySmall)
                p.steps.forEach { Text("• ${it.command.render()}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                Text(stringResource(R.string.plan_approval_note), style = MaterialTheme.typography.bodySmall)
                if (!connected) Text(stringResource(R.string.adb_required), color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::runPlan, enabled = connected && p.steps.isNotEmpty() && !plan.running) { Text(stringResource(R.string.plan_run_btn)) }
            }
        }
        if (plan.running) { Text(stringResource(R.string.plan_running)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (plan.outcomes.isNotEmpty()) {
            SectionCard {
                plan.outcomes.forEach { o ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(stringResource(stepStatusName(o.status)), stepTone(o.status))
                        Text(o.step.target, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    o.result?.takeIf { it.stderr.isNotBlank() }?.let { Text(it.stderr, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        if (plan.finished) {
            Text(
                stringResource(stopText(plan.stopped)), style = MaterialTheme.typography.bodyMedium,
                color = if (plan.stopped == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            if (plan.undoSteps > 0) {
                Button(onClick = vm::undoPlan, enabled = connected && !plan.running) { Text(stringResource(R.string.plan_undo_btn, plan.undoSteps)) }
            }
        }
    }
}

private fun stepTone(s: StepStatus): Tone = when (s) {
    StepStatus.CONFIRMED -> Tone.OK
    StepStatus.UNVERIFIED, StepStatus.REJECTED -> Tone.WARN
    StepStatus.FAILED -> Tone.ERROR
    StepStatus.PENDING, StepStatus.SKIPPED -> Tone.NEUTRAL
}

private fun planActionName(a: PlanAction): Int = when (a) {
    PlanAction.DISABLE -> R.string.plan_act_disable
    PlanAction.ENABLE -> R.string.plan_act_enable
    PlanAction.FORCE_STOP -> R.string.plan_act_force_stop
}

private fun stepStatusName(s: StepStatus): Int = when (s) {
    StepStatus.PENDING -> R.string.step_pending
    StepStatus.CONFIRMED -> R.string.step_confirmed
    StepStatus.UNVERIFIED -> R.string.step_unverified
    StepStatus.FAILED -> R.string.step_failed
    StepStatus.REJECTED -> R.string.step_rejected
    StepStatus.SKIPPED -> R.string.step_skipped
}

private fun stopText(r: StopReason?): Int = when (r) {
    null -> R.string.plan_done
    StopReason.STEP_FAILED -> R.string.plan_stopped_failed
    StopReason.STEP_UNVERIFIED -> R.string.plan_stopped_unverified
    StopReason.APPROVAL_DECLINED -> R.string.plan_stopped_declined
    StopReason.DISCONNECTED -> R.string.plan_stopped_disconnected
}

// ---------------------------------------------------------------- Terminal
@Composable
internal fun TerminalScreen(s: UiState, vm: MainViewModel) {
    var cmd by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val connected = s.adbState == AdbState.CONNECTED
    val entries = s.terminal.entries
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (s.level == AccessLevel.ADB) R.string.access_level_adb else R.string.access_level_standard), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.term_warning), style = MaterialTheme.typography.bodySmall)
        if (!connected) Text(stringResource(R.string.term_disconnected), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            cmd, { cmd = it.replace("\n", " ") }, singleLine = true, enabled = connected,
            shape = MaterialTheme.shapes.large,
            label = { Text(stringResource(R.string.term_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runTerminal(cmd) }, enabled = connected && !s.terminal.running && cmd.isNotBlank()) { Text(stringResource(R.string.term_run)) }
            OutlinedButton(onClick = vm::clearTerminal, enabled = entries.isNotEmpty()) { Text(stringResource(R.string.term_clear)) }
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(entries.joinToString("\n\n") { "$ ${it.command}\n${it.stdout}${it.stderr}" })) },
                enabled = entries.isNotEmpty(),
            ) { Text(stringResource(R.string.term_copy)) }
        }
        if (s.terminal.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries.asReversed()) { e ->
                SectionCard {
                    Text("$ ${e.command}", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    if (e.stdout.isNotBlank()) Text(e.stdout, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    if (e.stderr.isNotBlank()) Text(e.stderr, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                    e.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    val exit = when {
                        e.timedOut -> stringResource(R.string.term_timeout)
                        e.exitCode != null -> e.exitCode.toString()
                        else -> stringResource(R.string.unknown)
                    }
                    Text(stringResource(R.string.term_meta, exit, e.durationMs, formatTime(e.startedAtMillis)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Audit
@Composable
internal fun AuditScreen(s: UiState, vm: MainViewModel, actions: Actions) {
    val entries = s.audit.entries
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::exportAuditJson, enabled = entries.isNotEmpty()) { Text(stringResource(R.string.audit_export_json)) }
            OutlinedButton(onClick = vm::exportAuditCsv, enabled = entries.isNotEmpty()) { Text(stringResource(R.string.audit_export_csv)) }
            OutlinedButton(onClick = vm::askClearAudit, enabled = entries.isNotEmpty()) { Text(stringResource(R.string.audit_clear)) }
        }
        s.audit.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (entries.isEmpty()) Text(stringResource(R.string.audit_empty))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.result.operationId }) { e ->
                val r = e.result
                SectionCard {
                    Text("${r.name} • ${e.target}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            stringResource(outcomeLabel(r.outcome)),
                            when {
                                r.outcome == Outcome.SUCCEEDED && r.verified -> Tone.OK
                                r.outcome == Outcome.SUCCEEDED -> Tone.WARN
                                else -> Tone.ERROR
                            },
                        )
                        StatusPill(stringResource(if (r.verified) R.string.audit_verified else R.string.audit_not_verified), if (r.verified) Tone.OK else Tone.NEUTRAL)
                    }
                    Text(stringResource(rollbackText(r.rollback)), style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(r.timestampMillis), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Diagnostics
@Composable
internal fun DiagnosticsScreen(s: UiState, vm: MainViewModel, actions: Actions) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadDiagnostics() }
    val snap = s.diag.snapshot
    val connected = s.adbState == AdbState.CONNECTED
    val na = stringResource(R.string.diag_unreadable)
    ScreenColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::loadDiagnostics, enabled = !s.diag.loading) { Text(stringResource(R.string.diag_refresh)) }
            OutlinedButton(onClick = vm::exportDiagnostics, enabled = snap != null) { Text(stringResource(R.string.diag_export)) }
        }
        if (s.diag.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (snap != null) {
            SectionCard(title = stringResource(R.string.device_title)) {
                Text("${snap.manufacturer} ${snap.model}", style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.android_version, snap.release, snap.sdkInt))
                Text(stringResource(R.string.abis, snap.abis.joinToString()), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard {
                val st = snap.storage
                Text(
                    if (st == null) stringResource(R.string.diag_storage_na)
                    else stringResource(R.string.diag_storage, Formatter.formatFileSize(context, st.freeBytes), Formatter.formatFileSize(context, st.totalBytes)),
                )
                if (st != null) Text(st.scope, style = MaterialTheme.typography.labelSmall)
                val b = snap.battery
                Text(
                    if (b == null) stringResource(R.string.diag_battery_na)
                    else stringResource(R.string.diag_battery, b.percent?.toString() ?: na, b.temperatureC?.toString() ?: na, b.status ?: na),
                )
                val n = snap.network
                Text(
                    if (n == null) stringResource(R.string.diag_network_na)
                    else stringResource(R.string.diag_network, n.connected.toString(), n.transports.joinToString().ifEmpty { na }, n.metered?.toString() ?: na),
                )
                Text(stringResource(R.string.diag_packages, snap.appsTotal, snap.appsDisabled, snap.appsRemoved))
            }
            SectionCard(title = stringResource(R.string.diag_failures_title)) {
                if (snap.recentFailures.isEmpty()) Text(stringResource(R.string.diag_none))
                snap.recentFailures.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
        SectionCard {
            Text(stringResource(R.string.diag_logcat_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = vm::readLogcat, enabled = connected) { Text(stringResource(R.string.diag_logcat_btn)) }
            if (!connected) Text(stringResource(R.string.adb_required), style = MaterialTheme.typography.bodySmall)
            s.diag.logcatError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            s.diag.logcat?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
        }
    }
}

// ---------------------------------------------------------------- Settings
@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
    }
}

@Composable
internal fun SettingsScreen(s: UiState, vm: MainViewModel, actions: Actions) {
    val st = s.settings
    ScreenColumn {
        SectionCard(title = stringResource(R.string.set_theme)) {
            RadioRow(st.theme == ThemeMode.SYSTEM, stringResource(R.string.theme_system)) { vm.setTheme(ThemeMode.SYSTEM) }
            RadioRow(st.theme == ThemeMode.LIGHT, stringResource(R.string.theme_light)) { vm.setTheme(ThemeMode.LIGHT) }
            RadioRow(st.theme == ThemeMode.DARK, stringResource(R.string.theme_dark)) { vm.setTheme(ThemeMode.DARK) }
        }
        SectionCard(title = stringResource(R.string.set_audit_detail)) {
            RadioRow(st.auditDetail == AuditDetail.FULL, stringResource(R.string.detail_full)) { vm.setAuditDetail(AuditDetail.FULL) }
            RadioRow(st.auditDetail == AuditDetail.MINIMAL, stringResource(R.string.detail_minimal)) { vm.setAuditDetail(AuditDetail.MINIMAL) }
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_auto_connect), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.set_auto_connect_note), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = st.autoConnect, onCheckedChange = vm::setAutoConnect)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_save_terminal), Modifier.weight(1f))
                Switch(checked = st.saveTerminal, onCheckedChange = vm::setSaveTerminal)
            }
            OutlinedButton(onClick = vm::deleteSession) { Text(stringResource(R.string.set_delete_session)) }
        }
        SectionCard(title = stringResource(R.string.set_language)) {
            Text(stringResource(R.string.set_language_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = actions.openLocale, enabled = android.os.Build.VERSION.SDK_INT >= 33) { Text(stringResource(R.string.set_language_btn)) }
            if (android.os.Build.VERSION.SDK_INT < 33) Text(stringResource(R.string.set_language_unavailable), style = MaterialTheme.typography.labelSmall)
        }
        SectionCard(title = stringResource(R.string.set_allowlist)) {
            if (st.allowlist.isEmpty()) Text(stringResource(R.string.set_allowlist_empty), style = MaterialTheme.typography.bodySmall)
            st.allowlist.sorted().forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.toggleAllow(pkg) }) { Text(stringResource(R.string.allowlist_remove)) }
                }
            }
        }
        Text(stringResource(R.string.set_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Extensions
@Composable
internal fun ExtensionsScreen() {
    ScreenColumn {
        SectionCard(title = stringResource(R.string.ext_title)) {
            Text(stringResource(R.string.ext_body))
        }
    }
}
