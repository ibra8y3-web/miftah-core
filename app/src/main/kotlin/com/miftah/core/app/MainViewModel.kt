package com.miftah.core.app

import android.app.Application
import android.content.pm.PackageManager
import android.database.SQLException
import android.os.Build
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miftah.core.adb.LibAdbTransport
import com.miftah.core.audit.AuditStore
import com.miftah.core.capability.*
import com.miftah.core.execution.*
import com.miftah.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PendingOp(val label: String, val pkg: String, val command: ShellCommand, val isSystem: Boolean, val typed: Boolean)
data class PermView(val pkg: String, val entries: List<PermissionEntry>?, val error: String?)
data class AppOpsView(val pkg: String, val modes: Map<String, String?>, val loading: Boolean, val error: String?)

enum class PlanAction { DISABLE, ENABLE, FORCE_STOP }
data class PlanPreview(
    val action: PlanAction, val steps: List<PlanStep>, val systemCount: Int,
    val skippedAllowlist: Int, val skippedNotApplicable: Int, val rollback: RollbackAvailability,
)
data class ApprovalRequest(val index: Int, val total: Int, val commandText: String)
data class PlanUi(
    val action: PlanAction = PlanAction.DISABLE,
    val preview: PlanPreview? = null,
    val outcomes: List<StepOutcome> = emptyList(),
    val running: Boolean = false,
    val finished: Boolean = false,
    val stopped: StopReason? = null,
    val approval: ApprovalRequest? = null,
    val undoSteps: Int = 0,
)
data class TerminalUi(val entries: List<TerminalEntry> = emptyList(), val running: Boolean = false, val confirmCommand: String? = null)
data class DiagUi(
    val snapshot: DiagnosticsSnapshot? = null, val loading: Boolean = false, val preview: String? = null,
    val logcat: String? = null, val logcatError: String? = null,
)
data class AuditUi(
    val entries: List<AuditEntry> = emptyList(), val error: String? = null,
    val exportPreview: String? = null, val exportMime: String = "text/plain", val confirmClear: Boolean = false,
)

data class UiState(
    val loading: Boolean = true,
    val error: String? = null,
    val device: DeviceInfo? = null,
    val level: AccessLevel = AccessLevel.STANDARD,
    val apps: List<AppInfo> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val adbState: AdbState = AdbState.NOT_CONFIGURED,
    val adbError: String? = null,
    val adbIdentity: ShellIdentity? = null,
    val detectedPairingPort: Int? = null,
    val detectedConnectPort: Int? = null,
    val pairingActive: Boolean = false,
    val selectedApp: AppInfo? = null,
    val pending: PendingOp? = null,
    val busy: Boolean = false,
    val lastResult: OperationResult? = null,
    val lastUndo: PendingOp? = null,
    val ownPackage: String = "",
    val selection: Set<String> = emptySet(),
    val perm: PermView? = null,
    val appOps: AppOpsView? = null,
    val plan: PlanUi = PlanUi(),
    val terminal: TerminalUi = TerminalUi(),
    val diag: DiagUi = DiagUi(),
    val audit: AuditUi = AuditUi(),
    val settings: AppSettings = AppSettings(),
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val inventory = PackageInventory(app)
    private val engine = CapabilityEngine()
    private val miftah = app as MiftahApplication
    private val transport = miftah.transport
    private val discovery = miftah.discovery
    private val pairing = miftah.pairing
    private var lastAutoPort = -1
    private val executor = ExecutionEngine(transport)
    private val userId = Process.myUid() / 100_000
    private val sdk = Build.VERSION.SDK_INT
    private val auditStore = AuditStore(app)
    private val settingsRepo = SettingsRepository(app)
    private val permReader = PermissionReader(app)
    private val diagReader = DiagnosticsReader(app)
    private val verifiers = Verifiers(transport, userId, permissionState = { pkg, perm -> permReader.state(pkg, perm) })
    private val planRunner = PlanRunner(executor, isConnected = { transport.state.value == AdbState.CONNECTED })
    private val terminalRunner = TerminalRunner(transport)
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    private val _state = MutableStateFlow(
        UiState(capabilities = engine.evaluate(sdk, AccessLevel.STANDARD), ownPackage = app.packageName),
    )
    val state: StateFlow<UiState> = _state

    init {
        refresh()
        reloadAudit()
        viewModelScope.launch { settingsRepo.flow.collect { s -> _state.update { it.copy(settings = s) } } }
        discovery.startConnect()
        viewModelScope.launch {
            combine(discovery.pairingPort, discovery.connectPort, pairing.active) { p, c, a -> Triple(p, c, a) }
                .collect { (p, c, a) ->
                    _state.update { it.copy(detectedPairingPort = p, detectedConnectPort = c, pairingActive = a) }
                }
        }
        viewModelScope.launch {
            discovery.connectPort.filterNotNull().collect { port -> maybeAutoConnect(port) }
        }
        viewModelScope.launch {
            combine(transport.state, transport.lastError, transport.identity) { s, e, i -> Triple(s, e, i) }
                .collect { (s, e, i) ->
                    val level = if (s == AdbState.CONNECTED) AccessLevel.ADB else AccessLevel.STANDARD
                    _state.update {
                        it.copy(adbState = s, adbError = e, adbIdentity = i, level = level,
                            capabilities = engine.evaluate(sdk, level))
                    }
                }
        }
    }

    override fun onCleared() {
        discovery.stopConnect()
        super.onCleared()
    }

    /** Connects by itself, once per advertised port, only if this phone is already paired and the setting is on. */
    private suspend fun maybeAutoConnect(port: Int) {
        val s = _state.value
        if (!s.settings.autoConnect || port == lastAutoPort || s.adbState != AdbState.DISCONNECTED) return
        lastAutoPort = port
        transport.connect(LOOPBACK, port)
    }

    // ---------------- inventory ----------------
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val device = DeviceInfo.read()
                val apps = inventory.load()
                _state.update {
                    it.copy(loading = false, device = device, apps = apps, capabilities = engine.evaluate(device.sdkInt, it.level))
                }
            } catch (e: SecurityException) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            } catch (e: RuntimeException) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: AppFilter) = _state.update { it.copy(filter = f) }

    // ---------------- ADB ----------------
    fun pair(port: String, code: String) { viewModelScope.launch { transport.pair(LOOPBACK, port.toIntOrNull() ?: -1, code) } }
    fun connect(port: String) { viewModelScope.launch { transport.connect(LOOPBACK, port.toIntOrNull() ?: -1) } }
    fun beginAutoPairing() { pairing.begin() }
    fun cancelAutoPairing() { pairing.cancel() }
    fun pairWithCode(code: String) { viewModelScope.launch { pairing.submitCode(code, notify = false) } }
    fun connectDetected() {
        val port = _state.value.detectedConnectPort ?: -1
        viewModelScope.launch { transport.connect(LOOPBACK, port) }
    }
    fun disconnect() { viewModelScope.launch { transport.disconnect() } }
    fun clearPairing() { viewModelScope.launch { transport.clearPairing() } }

    // ---------------- audit ----------------
    private fun reloadAudit() {
        viewModelScope.launch {
            try {
                val list = auditStore.recent()
                _state.update { it.copy(audit = it.audit.copy(entries = list, error = null)) }
            } catch (e: SQLException) {
                _state.update { it.copy(audit = it.audit.copy(error = e.message ?: "audit read failed")) }
            }
        }
    }

    private suspend fun audit(target: String, result: OperationResult) {
        val minimal = _state.value.settings.auditDetail == AuditDetail.MINIMAL
        val r = if (minimal) result.copy(stdout = "", stderr = "") else result
        try {
            auditStore.record(AuditEntry(target, r))
            val list = auditStore.recent()
            _state.update { it.copy(audit = it.audit.copy(entries = list, error = null)) }
        } catch (e: SQLException) {
            _state.update { it.copy(audit = it.audit.copy(error = e.message ?: "audit write failed")) }
        }
    }

    fun exportAuditJson() = _state.update { it.copy(audit = it.audit.copy(exportPreview = AuditExport.toJson(it.audit.entries), exportMime = "application/json")) }
    fun exportAuditCsv() = _state.update { it.copy(audit = it.audit.copy(exportPreview = AuditExport.toCsv(it.audit.entries), exportMime = "text/csv")) }
    fun dismissAuditExport() = _state.update { it.copy(audit = it.audit.copy(exportPreview = null)) }
    fun askClearAudit() = _state.update { it.copy(audit = it.audit.copy(confirmClear = true)) }
    fun cancelClearAudit() = _state.update { it.copy(audit = it.audit.copy(confirmClear = false)) }
    fun confirmClearAudit() {
        _state.update { it.copy(audit = it.audit.copy(confirmClear = false)) }
        viewModelScope.launch {
            try {
                auditStore.clear()
                _state.update { it.copy(audit = it.audit.copy(entries = emptyList(), error = null)) }
            } catch (e: SQLException) {
                _state.update { it.copy(audit = it.audit.copy(error = e.message ?: "audit clear failed")) }
            }
        }
    }

    // ---------------- single operations ----------------
    fun selectApp(a: AppInfo?) {
        _state.update { it.copy(selectedApp = a, perm = null, appOps = null) }
        if (a != null && a.installed) loadPermissions(a.packageName)
    }

    fun toggleSelect(pkg: String) = _state.update { it.copy(selection = if (pkg in it.selection) it.selection - pkg else it.selection + pkg) }
    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    private fun ask(app: AppInfo, build: () -> ShellCommand) {
        val cmd = try { build() } catch (e: IllegalArgumentException) {
            _state.update { it.copy(lastResult = rejected("invalid", e.message ?: "invalid argument")) }
            return
        }
        _state.update { it.copy(pending = PendingOp(app.label, app.packageName, cmd, app.isSystem, cmd is ShellCommand.UninstallForUser)) }
    }

    fun askDisable(a: AppInfo) = ask(a) { ShellCommand.DisableUser(a.packageName, userId) }
    fun askEnable(a: AppInfo) = ask(a) { ShellCommand.Enable(a.packageName, userId) }
    fun askForceStop(a: AppInfo) = ask(a) { ShellCommand.ForceStop(a.packageName) }
    fun askUninstall(a: AppInfo) = ask(a) { ShellCommand.UninstallForUser(a.packageName, userId) }
    fun askRestore(a: AppInfo) = ask(a) { ShellCommand.InstallExisting(a.packageName, userId) }
    fun askGrant(a: AppInfo, perm: String) = ask(a) { ShellCommand.GrantPermission(a.packageName, perm, userId) }
    fun askRevoke(a: AppInfo, perm: String) = ask(a) { ShellCommand.RevokePermission(a.packageName, perm, userId) }
    fun askAppOp(a: AppInfo, op: String, mode: String) = ask(a) { ShellCommand.AppOpsSet(a.packageName, op, mode, userId) }

    fun cancelPending() = _state.update { it.copy(pending = null) }
    fun dismissResult() = _state.update { it.copy(lastResult = null, lastUndo = null) }

    fun askUndo() {
        val u = _state.value.lastUndo ?: return
        _state.update { it.copy(lastResult = null, lastUndo = null, pending = u.copy(typed = u.command is ShellCommand.UninstallForUser)) }
    }

    fun confirmPending() {
        val p = _state.value.pending ?: return
        _state.update { it.copy(pending = null, selectedApp = null, busy = true) }
        viewModelScope.launch {
            val result = execute(p)
            val undo = if (result.isConfirmedSuccess) {
                Rollback.inverse(p.command, result.previousState)?.let { PendingOp(p.label, p.pkg, it, p.isSystem, false) }
            } else null
            _state.update { it.copy(busy = false, lastResult = result, lastUndo = undo) }
            refresh()
        }
    }

    private suspend fun execute(p: PendingOp): OperationResult {
        if (p.pkg == _state.value.ownPackage) return rejected(p.command.name, "Refusing to modify MIFTAH Core itself")
        val result = executor.run(p.command, confirmed = true, verification = verifiers.forCommand(p.command))
        audit(p.pkg, result)
        return result
    }

    private fun rejected(name: String, message: String) = OperationResult(
        UUID.randomUUID().toString(), name, System.currentTimeMillis(), _state.value.level, Outcome.REJECTED,
        null, "", message, null, null, false, RollbackAvailability.UNAVAILABLE, null,
    )

    // ---------------- permissions / appops ----------------
    private fun loadPermissions(pkg: String) {
        viewModelScope.launch {
            try {
                val entries = permReader.read(pkg)
                _state.update { it.copy(perm = PermView(pkg, entries, null)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PackageManager.NameNotFoundException) {
                _state.update { it.copy(perm = PermView(pkg, null, "package not found")) }
            } catch (e: Exception) {
                _state.update { it.copy(perm = PermView(pkg, null, e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun readAppOps(a: AppInfo) {
        val pkg = a.packageName
        _state.update { it.copy(appOps = AppOpsView(pkg, emptyMap(), true, null)) }
        viewModelScope.launch {
            try {
                val modes = LinkedHashMap<String, String?>()
                for (op in KnownAppOps) {
                    val r = executor.run(ShellCommand.AppOpsGet(pkg, op, userId))
                    modes[op] = if (r.outcome == Outcome.SUCCEEDED && r.exitCode == 0) ShellParsers.parseAppOpMode(r.stdout, op) else null
                }
                _state.update { it.copy(appOps = AppOpsView(pkg, modes, false, null)) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(appOps = AppOpsView(pkg, emptyMap(), false, e.message)) }
            }
        }
    }

    // ---------------- plans ----------------
    fun setPlanAction(a: PlanAction) = _state.update { it.copy(plan = it.plan.copy(action = a, preview = null)) }

    fun buildPreview() {
        val s = _state.value
        val action = s.plan.action
        val byPkg = s.apps.associateBy { it.packageName }
        val steps = mutableListOf<PlanStep>()
        var system = 0
        var allow = 0
        var notApplicable = 0
        for (pkg in s.selection.sorted()) {
            val app = byPkg[pkg] ?: continue
            if (pkg == s.ownPackage || !app.installed) { notApplicable++; continue }
            if (pkg in s.settings.allowlist) { allow++; continue }
            val applicable = when (action) {
                PlanAction.DISABLE -> app.enabled
                PlanAction.ENABLE -> !app.enabled
                PlanAction.FORCE_STOP -> true
            }
            if (!applicable) { notApplicable++; continue }
            val cmd = try {
                when (action) {
                    PlanAction.DISABLE -> ShellCommand.DisableUser(pkg, userId)
                    PlanAction.ENABLE -> ShellCommand.Enable(pkg, userId)
                    PlanAction.FORCE_STOP -> ShellCommand.ForceStop(pkg)
                }
            } catch (e: IllegalArgumentException) { notApplicable++; continue }
            steps += PlanStep(steps.size, pkg, cmd, verifiers.forCommand(cmd))
            if (app.isSystem) system++
        }
        val rollback = if (action == PlanAction.FORCE_STOP) RollbackAvailability.UNAVAILABLE else RollbackAvailability.AVAILABLE
        val preview = PlanPreview(action, steps, system, allow, notApplicable, rollback)
        _state.update { it.copy(plan = it.plan.copy(preview = preview, outcomes = emptyList(), finished = false, stopped = null, undoSteps = 0)) }
    }

    fun runPlan() { _state.value.plan.preview?.let { runSteps(it.steps) } }

    fun undoPlan() {
        val steps = _state.value.plan.outcomes
            .filter { it.status == StepStatus.CONFIRMED }
            .asReversed()
            .mapNotNull { o ->
                val inv = Rollback.inverse(o.step.command, o.result?.previousState) ?: return@mapNotNull null
                PlanStep(0, o.step.target, inv, verifiers.forCommand(inv))
            }
            .mapIndexed { i, st -> st.copy(id = i) }
        if (steps.isNotEmpty()) runSteps(steps)
    }

    private fun runSteps(steps: List<PlanStep>) {
        if (_state.value.plan.running) return
        val initial = steps.map { StepOutcome(it, StepStatus.PENDING, null) }
        _state.update { it.copy(plan = it.plan.copy(running = true, finished = false, stopped = null, outcomes = initial, undoSteps = 0)) }
        viewModelScope.launch {
            val res = planRunner.run(
                steps,
                approve = { step, i, total -> requestApproval(step, i, total) },
                onResult = { step, r -> audit(step.target, r) },
                onProgress = { o -> _state.update { it.copy(plan = it.plan.copy(outcomes = o)) } },
            )
            val undoable = res.outcomes.count {
                it.status == StepStatus.CONFIRMED && Rollback.inverse(it.step.command, it.result?.previousState) != null
            }
            _state.update {
                it.copy(plan = it.plan.copy(running = false, finished = true, stopped = res.stoppedReason, outcomes = res.outcomes, undoSteps = undoable))
            }
            refresh()
        }
    }

    private suspend fun requestApproval(step: PlanStep, index: Int, total: Int): Boolean {
        val d = CompletableDeferred<Boolean>()
        approvalDeferred = d
        _state.update { it.copy(plan = it.plan.copy(approval = ApprovalRequest(index + 1, total, step.command.render()))) }
        val ok = d.await()
        _state.update { it.copy(plan = it.plan.copy(approval = null)) }
        return ok
    }

    fun answerApproval(ok: Boolean) { approvalDeferred?.complete(ok) }

    // ---------------- terminal ----------------
    fun runTerminal(text: String) {
        val c = text.trim()
        if (c.isEmpty() || _state.value.terminal.running) return
        if (CommandRisk.classify(c) == Risk.DESTRUCTIVE) {
            _state.update { it.copy(terminal = it.terminal.copy(confirmCommand = c)) }
            return
        }
        doTerminal(c)
    }

    fun confirmTerminal() {
        val c = _state.value.terminal.confirmCommand ?: return
        _state.update { it.copy(terminal = it.terminal.copy(confirmCommand = null)) }
        doTerminal(c)
    }

    fun cancelTerminal() = _state.update { it.copy(terminal = it.terminal.copy(confirmCommand = null)) }
    fun clearTerminal() = _state.update { it.copy(terminal = it.terminal.copy(entries = emptyList())) }

    private fun doTerminal(c: String) {
        _state.update { it.copy(terminal = it.terminal.copy(running = true)) }
        viewModelScope.launch {
            val e = terminalRunner.run(c)
            _state.update { it.copy(terminal = it.terminal.copy(entries = it.terminal.entries + e, running = false)) }
            val outcome = when {
                e.error != null -> Outcome.FAILED
                e.timedOut -> Outcome.TIMED_OUT
                e.exitCode == 0 -> Outcome.SUCCEEDED
                else -> Outcome.FAILED
            }
            val result = OperationResult(
                UUID.randomUUID().toString(), "terminal", e.startedAtMillis, _state.value.level, outcome, e.exitCode,
                e.stdout, e.error ?: e.stderr, null, null, false, RollbackAvailability.UNAVAILABLE, "interactive command",
            )
            audit(Redactor.redact(c), result)
            if (_state.value.settings.saveTerminal) appendSession(e)
        }
    }

    private val sessionFile get() = File(app.filesDir, "terminal_session.txt")

    private suspend fun appendSession(e: TerminalEntry) = withContext(Dispatchers.IO) {
        sessionFile.appendText("$ ${Redactor.redact(e.command)}\n${e.stdout}${e.stderr}\n[exit ${e.exitCode ?: "unknown"}]\n\n")
    }

    fun deleteSession() { viewModelScope.launch(Dispatchers.IO) { sessionFile.delete() } }

    // ---------------- diagnostics ----------------
    fun loadDiagnostics() {
        _state.update { it.copy(diag = it.diag.copy(loading = true)) }
        viewModelScope.launch {
            val s = _state.value
            val d = s.device ?: DeviceInfo.read()
            val failures = s.audit.entries
                .filter { !it.result.isConfirmedSuccess && it.result.name != "terminal" }
                .take(5)
                .map { "${it.result.name} ${it.target}: ${it.result.outcome}" }
            val snap = DiagnosticsSnapshot(
                d.manufacturer, d.model, d.release, d.sdkInt, d.abis,
                diagReader.storage(), diagReader.battery(), diagReader.network(),
                s.apps.count { it.installed }, s.apps.count { it.installed && !it.enabled }, s.apps.count { !it.installed },
                failures, System.currentTimeMillis(),
            )
            _state.update { it.copy(diag = it.diag.copy(snapshot = snap, loading = false)) }
        }
    }

    fun exportDiagnostics() {
        val snap = _state.value.diag.snapshot ?: return
        _state.update { it.copy(diag = it.diag.copy(preview = DiagnosticsReport.toText(snap))) }
    }

    fun dismissDiagPreview() = _state.update { it.copy(diag = it.diag.copy(preview = null)) }

    fun readLogcat() {
        viewModelScope.launch {
            val r = executor.run(ShellCommand.Logcat(200), timeoutMs = 20_000)
            _state.update {
                if (r.outcome == Outcome.SUCCEEDED && r.exitCode == 0) it.copy(diag = it.diag.copy(logcat = r.stdout.takeLast(20_000), logcatError = null))
                else it.copy(diag = it.diag.copy(logcat = null, logcatError = r.stderr.ifBlank { r.outcome.name }))
            }
        }
    }

    // ---------------- settings ----------------
    fun setTheme(t: ThemeMode) { viewModelScope.launch { settingsRepo.setTheme(t) } }
    fun setAuditDetail(d: AuditDetail) { viewModelScope.launch { settingsRepo.setAuditDetail(d) } }
    fun setSaveTerminal(v: Boolean) { viewModelScope.launch { settingsRepo.setSaveTerminal(v) } }
    fun setAutoConnect(v: Boolean) { viewModelScope.launch { settingsRepo.setAutoConnect(v) } }
    fun toggleAllow(pkg: String) { viewModelScope.launch { settingsRepo.toggleAllow(pkg) } }

    private companion object { const val LOOPBACK = "127.0.0.1" }
}
