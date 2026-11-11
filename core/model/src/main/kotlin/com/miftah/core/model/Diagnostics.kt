package com.miftah.core.model

data class StorageSnapshot(val totalBytes: Long, val freeBytes: Long, val scope: String)

data class BatterySnapshot(val percent: Int?, val temperatureC: Float?, val status: String?, val plugged: Boolean?) {
    companion object {
        fun percentOf(level: Int, scale: Int): Int? = if (level < 0 || scale <= 0) null else level * 100 / scale
    }
}

data class NetworkSnapshot(val connected: Boolean, val transports: List<String>, val metered: Boolean?)

data class DiagnosticsSnapshot(
    val manufacturer: String,
    val model: String,
    val release: String,
    val sdkInt: Int,
    val abis: List<String>,
    val storage: StorageSnapshot?,
    val battery: BatterySnapshot?,
    val network: NetworkSnapshot?,
    val appsTotal: Int,
    val appsDisabled: Int,
    val appsRemoved: Int,
    val recentFailures: List<String>,
    val readAtMillis: Long,
)

object DiagnosticsReport {
    fun toText(s: DiagnosticsSnapshot): String = buildString {
        appendLine("MIFTAH Core diagnostics (read at ${s.readAtMillis} ms since epoch)")
        appendLine("Device: ${s.manufacturer} ${s.model}")
        appendLine("Android: ${s.release} (API ${s.sdkInt}), ABIs: ${s.abis.joinToString()}")
        s.storage?.let { appendLine("Storage (${it.scope}): ${it.freeBytes} bytes free of ${it.totalBytes}") }
            ?: appendLine("Storage: not readable")
        s.battery?.let { appendLine("Battery: ${it.percent ?: "?"}%, ${it.temperatureC ?: "?"} C, status ${it.status ?: "?"}, plugged ${it.plugged ?: "?"}") }
            ?: appendLine("Battery: not readable")
        s.network?.let { appendLine("Network: connected=${it.connected}, transports=${it.transports.joinToString()}, metered=${it.metered ?: "?"}") }
            ?: appendLine("Network: not readable")
        appendLine("Packages: ${s.appsTotal} installed, ${s.appsDisabled} disabled, ${s.appsRemoved} removed for current user")
        if (s.recentFailures.isEmpty()) appendLine("Recent failed operations: none") else {
            appendLine("Recent failed operations:")
            s.recentFailures.forEach { appendLine("- $it") }
        }
    }
}
