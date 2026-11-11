package com.miftah.core.app

import android.app.Application
import android.content.pm.PackageManager
import android.database.SQLException
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miftah.core.adb.LibAdbTransport
import com.miftah.core.audit.AuditStore
import com.miftah.core.capability.*
import com.miftah.core.execution.*
import com.miftah.core.model.*
import com.miftah.core.net.BlocklistParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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

data class PrivacyUi(val report: PrivacyReport? = null, val loading: Boolean = false, val error: String? = null)
data class StartupUi(val entries: List<StartupEntry> = emptyList(), val loading: Boolean = false, val error: String? = null)
data class DisplayUi(
    val physical: Int? = null, val override: Int? = null, val scales: Map<String, String?> = emptyMap(),
    val loading: Boolean = false, val error: String? = null, val revertIn: Int? = null,
)
data class ShieldUi(
    val counts: BlocklistCounts = BlocklistCounts(0, 0, 0, 0), val importing: Boolean = false,
    val message: String? = null, val privateDns: String? = null, val systemVpn: Boolean? = null,
)
data class SensorsView(val pkg: String, val lastAccess: Map<String, String?>, val loading: Boolean, val error: String?)

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
    val privacy: PrivacyUi = PrivacyUi(),
    val startup: StartupUi = StartupUi(),
    val display: DisplayUi = DisplayUi(),
    val shield: ShieldUi = ShieldUi(),
    val vpn: VpnUiState = VpnUiState(),
    val sensors: SensorsView? = null,
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
    private val privacyScanner = PrivacyScanner(app)
    private val startupScanner = StartupScanner(app)
    private val verifiers = Verifiers(
        transport, userId,
        permissionState = { pkg, perm -> permReader.state(pkg, perm) },
        componentState = { pkg, cls -> startupScanner.state(pkg, cls) },
    )
    private var revertJob: Job? = null
    private var pendingInverse: ShellCommand? = null
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
        viewModelScope.launch {
            VpnRuntime.state.collect { v ->
                _state.update { it.copy(vpn = v) }
                delay(800)
                _state.update { it.copy(shield = it.shield.copy(systemVpn = diagReader.vpnActive())) }
            }
        }
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
        _state.update { it.copy(selectedApp = a, perm = null, appOps = null, sensors = null) }
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
            val isDensity = p.command is ShellCommand.WmDensity
            val startsCountdown = isDensity && result.outcome == Outcome.SUCCEEDED
            val undo = if (result.isConfirmedSuccess && !isDensity) {
                Rollback.inverse(p.command, result.previousState)?.let { PendingOp(p.label, p.pkg, it, p.isSystem, false) }
            } else null
            _state.update { it.copy(busy = false, lastResult = if (startsCountdown) null else result, lastUndo = undo) }
            refresh()
            when (p.command) {
                is ShellCommand.WmDensity -> if (startsCountdown) startRevertCountdown(p.command, result)
                is ShellCommand.WmDensityReset -> loadDisplay()
                is ShellCommand.DisableComponent, is ShellCommand.EnableComponent -> loadStartup()
                else -> {}
            }
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

    // ---------------- privacy doctor ----------------
    fun scanPrivacy() {
        _state.update { it.copy(privacy = it.privacy.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val report = privacyScanner.scan()
                _state.update { it.copy(privacy = PrivacyUi(report, false, null)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(privacy = it.privacy.copy(loading = false, error = e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun readSensors(a: AppInfo) {
        val pkg = a.packageName
        _state.update { it.copy(sensors = SensorsView(pkg, emptyMap(), true, null)) }
        viewModelScope.launch {
            try {
                val map = LinkedHashMap<String, String?>()
                for (op in SENSOR_OPS) {
                    val r = executor.run(ShellCommand.AppOpsGet(pkg, op, userId))
                    map[op] = if (r.outcome == Outcome.SUCCEEDED && r.exitCode == 0) ShellParsers.parseAppOpLastAccess(r.stdout, op) else null
                }
                _state.update { it.copy(sensors = SensorsView(pkg, map, false, null)) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(sensors = SensorsView(pkg, emptyMap(), false, e.message)) }
            }
        }
    }

    // ---------------- startup manager ----------------
    fun loadStartup() {
        _state.update { it.copy(startup = it.startup.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val list = startupScanner.scan()
                _state.update { it.copy(startup = StartupUi(list, false, null)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(startup = it.startup.copy(loading = false, error = e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun askToggleComponent(entry: StartupEntry, component: String, enable: Boolean) {
        val cmd = try {
            if (enable) ShellCommand.EnableComponent(entry.pkg, component, userId) else ShellCommand.DisableComponent(entry.pkg, component, userId)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(lastResult = rejected("component", e.message ?: "invalid component")) }
            return
        }
        _state.update { it.copy(pending = PendingOp(entry.label, entry.pkg, cmd, entry.isSystem, false)) }
    }

    // ---------------- display tweaks ----------------
    fun loadDisplay() {
        if (_state.value.adbState != AdbState.CONNECTED) return
        _state.update { it.copy(display = it.display.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val d = executor.run(ShellCommand.WmDensityQuery)
            val info = if (d.outcome == Outcome.SUCCEEDED && d.exitCode == 0) ShellParsers.parseDensity(d.stdout) else null
            val scales = LinkedHashMap<String, String?>()
            for (k in Validators.ANIM_KEYS) {
                val r = executor.run(ShellCommand.SettingsGetGlobal(k))
                scales[k] = if (r.outcome == Outcome.SUCCEEDED && r.exitCode == 0) {
                    r.stdout.trim().let { if (it.isEmpty() || it == "null") "default" else it }
                } else null
            }
            _state.update {
                it.copy(display = it.display.copy(
                    physical = info?.physical, override = info?.override, scales = scales, loading = false,
                    error = if (info == null) "unreadable" else null,
                ))
            }
        }
    }

    /** dpi == null means "back to the default density". */
    fun askDensity(dpi: Int?) {
        val cmd = try {
            if (dpi == null) ShellCommand.WmDensityReset else ShellCommand.WmDensity(dpi)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(lastResult = rejected("wm-density", e.message ?: "invalid density")) }
            return
        }
        _state.update { it.copy(pending = PendingOp("Display", "wm density", cmd, false, false)) }
    }

    fun setAnimationScale(value: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            var last: OperationResult? = null
            for (key in Validators.ANIM_KEYS) {
                val cmd = try { ShellCommand.SettingsPutGlobal(key, value) } catch (e: IllegalArgumentException) {
                    last = rejected(key, e.message ?: "invalid value"); break
                }
                val r = executor.run(cmd, verification = verifiers.forCommand(cmd))
                audit(key, r)
                last = r
                if (!r.isConfirmedSuccess) break
            }
            _state.update { it.copy(busy = false, lastResult = last, lastUndo = null) }
            loadDisplay()
        }
    }

    private fun startRevertCountdown(command: ShellCommand, result: OperationResult) {
        val inverse = Rollback.inverse(command, result.previousState) ?: ShellCommand.WmDensityReset
        pendingInverse = inverse
        revertJob?.cancel()
        revertJob = viewModelScope.launch {
            for (sec in REVERT_SECONDS downTo 1) {
                _state.update { it.copy(display = it.display.copy(revertIn = sec)) }
                delay(1000)
            }
            performRevert(inverse)
        }
    }

    private suspend fun performRevert(inverse: ShellCommand) {
        _state.update { it.copy(display = it.display.copy(revertIn = null), busy = true) }
        val r = executor.run(inverse, confirmed = true, verification = verifiers.forCommand(inverse))
        audit("display", r)
        _state.update { it.copy(busy = false, lastResult = r, lastUndo = null) }
        loadDisplay()
    }

    fun keepDisplay() {
        revertJob?.cancel()
        _state.update { it.copy(display = it.display.copy(revertIn = null)) }
        loadDisplay()
    }

    fun revertDisplayNow() {
        val inverse = pendingInverse ?: return
        revertJob?.cancel()
        viewModelScope.launch { performRevert(inverse) }
    }

    // ---------------- shield (ad blocker + firewall) ----------------
    fun refreshShield() {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) { BlocklistFiles.counts(app) }
            val pdns = try { Settings.Global.getString(app.contentResolver, "private_dns_mode") } catch (e: SecurityException) { null }
            _state.update { it.copy(shield = it.shield.copy(counts = counts, privateDns = pdns, systemVpn = diagReader.vpnActive())) }
        }
    }

    fun setUpstream(u: Upstream) { viewModelScope.launch { settingsRepo.setUpstream(u) } }
    fun toggleExcluded(pkg: String) { viewModelScope.launch { settingsRepo.toggleExcluded(pkg) } }
    fun toggleBlocked(pkg: String) { viewModelScope.launch { settingsRepo.toggleBlocked(pkg) } }
    fun addSelectionToExcluded() { val sel = _state.value.selection; viewModelScope.launch { settingsRepo.addExcluded(sel) } }
    fun addSelectionToBlocked() { val sel = _state.value.selection - _state.value.ownPackage; viewModelScope.launch { settingsRepo.addBlocked(sel) } }

    private fun shieldMessage(text: String?) = _state.update { it.copy(shield = it.shield.copy(message = text)) }

    fun addDomain(text: String, allow: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BlocklistFiles.add(app, if (allow) BlocklistFiles.ALLOW else BlocklistFiles.CUSTOM, text)
            }
            shieldMessage(if (ok) null else app.getString(R.string.shield_bad_domain))
            refreshShield()
        }
    }

    fun clearList(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { BlocklistFiles.clear(app, name) }
            refreshShield()
        }
    }

    /** Downloads a list only from an https address the user typed. */
    fun importBlocklist(urlText: String) {
        if (_state.value.shield.importing) return
        _state.update { it.copy(shield = it.shield.copy(importing = true, message = null)) }
        viewModelScope.launch {
            val (domains, error) = withContext(Dispatchers.IO) { download(urlText.trim()) }
            if (domains != null && domains.isNotEmpty()) {
                withContext(Dispatchers.IO) { BlocklistFiles.writeImported(app, domains) }
                shieldMessage(app.getString(R.string.shield_import_ok, domains.size))
            } else {
                shieldMessage(app.getString(R.string.shield_import_failed, error ?: "empty list"))
            }
            _state.update { it.copy(shield = it.shield.copy(importing = false)) }
            refreshShield()
        }
    }

    private fun download(urlText: String): Pair<Set<String>?, String?> {
        val url = try { URL(urlText) } catch (e: IOException) { return null to "invalid address" }
        if (url.protocol != "https") return null to "only https addresses are accepted"
        val conn = try { url.openConnection() as HttpURLConnection } catch (e: IOException) { return null to (e.message ?: "connection failed") }
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            if (conn.responseCode !in 200..299) return null to "HTTP ${conn.responseCode}"
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_LIST_BYTES) return null to "list is larger than 8 MB"
                }
            }
            BlocklistParser.parse(out.toString(Charsets.UTF_8.name()), 300_000) to null
        } catch (e: IOException) {
            null to (e.message ?: "download failed")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val REVERT_SECONDS = 15
        const val MAX_LIST_BYTES = 8 * 1024 * 1024
        val SENSOR_OPS = listOf("CAMERA", "RECORD_AUDIO", "FINE_LOCATION")
    }
}
