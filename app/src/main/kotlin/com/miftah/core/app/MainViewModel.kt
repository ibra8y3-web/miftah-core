package com.miftah.core.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miftah.core.capability.CapabilityEngine
import com.miftah.core.capability.DeviceInfo
import com.miftah.core.capability.PackageInventory
import com.miftah.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val error: String? = null,
    val device: DeviceInfo? = null,
    val level: AccessLevel = AccessLevel.STANDARD,
    val apps: List<AppInfo> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val inventory = PackageInventory(app)
    private val engine = CapabilityEngine()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val device = DeviceInfo.read()
                val apps = inventory.load()
                // No ADB/root transport exists in this build, so the held level is STANDARD.
                val level = AccessLevel.STANDARD
                _state.update {
                    it.copy(loading = false, device = device, level = level, apps = apps,
                        capabilities = engine.evaluate(device.sdkInt, level))
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
}
