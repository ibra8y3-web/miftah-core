package com.miftah.core.app

import android.app.Application
import android.os.Build
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miftah.core.adb.LibAdbTransport
import com.miftah.core.capability.CapabilityEngine
import com.miftah.core.capability.DeviceInfo
import com.miftah.core.capability.PackageInventory
import com.miftah.core.execution.*
import com.miftah.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppAction { DISABLE, ENABLE }
data class PendingAction(val app: AppInfo, val action: AppAction)

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
    val selectedApp: AppInfo? = null,
    val pending: PendingAction? = null,
    val busy: Boolean = false,
    val lastResult: OperationResult? = null,
    val ownPackage: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val inventory = PackageInventory(app)
    private val engine = CapabilityEngine()
    private val transport = LibAdbTransport(app)
    private val executor = ExecutionEngine(transport)
    private val userId = Process.myUid() / 100_000
    private val sdk = Build.VERSION.SDK_INT

    private val _state = MutableStateFlow(
        UiState(capabilities = engine.evaluate(sdk, AccessLevel.STANDARD), ownPackage = app.packageName),
    )
    val state: StateFlow<UiState> = _state

    init {
        refresh()
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

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val device = DeviceInfo.read()
                val apps = inventory.load()
                _state.update {
                    it.copy(loading = false, device = device, apps = apps,
                        capabilities = engine.evaluate(device.sdkInt, it.level))
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

    // ---- ADB ----
    fun pair(port: String, code: String) {
        viewModelScope.launch { transport.pair(LOOPBACK, port.toIntOrNull() ?: -1, code) }
    }
    fun connect(port: String) {
        viewModelScope.launch { transport.connect(LOOPBACK, port.toIntOrNull() ?: -1) }
    }
    fun disconnect() { viewModelScope.launch { transport.disconnect() } }
    fun clearPairing() { viewModelScope.launch { transport.clearPairing() } }

    // ---- App management ----
    fun selectApp(a: AppInfo?) = _state.update { it.copy(selectedApp = a) }
    fun askConfirm(a: AppInfo, action: AppAction) = _state.update { it.copy(pending = PendingAction(a, action)) }
    fun cancelPending() = _state.update { it.copy(pending = null) }
    fun dismissResult() = _state.update { it.copy(lastResult = null) }

    fun confirmPending() {
        val p = _state.value.pending ?: return
        _state.update { it.copy(pending = null, selectedApp = null, busy = true) }
        viewModelScope.launch {
            val result = runAction(p)
            _state.update { it.copy(busy = false, lastResult = result) }
            refresh()
        }
    }

    private suspend fun runAction(p: PendingAction): OperationResult {
        val pkg = p.app.packageName
        val name = p.action.name.lowercase()
        if (pkg == _state.value.ownPackage) return rejected(name, "Refusing to modify MIFTAH Core itself")
        return try {
            val probe = PackageStateProbe(transport, userId)
            when (p.action) {
                AppAction.DISABLE -> executor.run(
                    ShellCommand.DisableUser(pkg, userId), confirmed = true,
                    verification = Verifications.disabled(probe, pkg),
                )
                AppAction.ENABLE -> executor.run(
                    ShellCommand.Enable(pkg, userId),
                    verification = Verifications.enabled(probe, pkg),
                )
            }
        } catch (e: IllegalArgumentException) {
            rejected(name, e.message ?: "invalid argument")
        }
    }

    private fun rejected(name: String, message: String) = OperationResult(
        UUID.randomUUID().toString(), name, System.currentTimeMillis(), _state.value.level, Outcome.REJECTED,
        null, "", message, null, null, false, RollbackAvailability.UNAVAILABLE, null,
    )

    private companion object { const val LOOPBACK = "127.0.0.1" }
}
