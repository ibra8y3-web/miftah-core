package com.miftah.core.execution

internal suspend fun AdbTransport.tryExecute(command: String): RawShellResult? =
    try { execute(command) } catch (e: TransportException) { null }

class ProcessStateProbe(private val transport: AdbTransport) {
    suspend fun read(pkg: String): String? {
        val raw = transport.tryExecute(ShellCommand.PidOf(pkg).render()) ?: return null
        val out = raw.stdout.trim()
        return when {
            raw.exitCode == 0 && Regex("^[0-9 ]+$").matches(out) -> "running"
            raw.exitCode == 1 && out.isEmpty() -> "stopped"
            else -> null
        }
    }
}

class PackagePresenceProbe(private val transport: AdbTransport, private val userId: Int) {
    suspend fun read(pkg: String): String? {
        Validators.requirePackage(pkg)
        val raw = transport.tryExecute(ShellCommand.ListPackages(userId).render()) ?: return null
        if (raw.exitCode != 0) return null
        val parsed = ShellParsers.parsePackageList(raw.stdout)
        if (parsed.malformedLines > 0) return null
        return if (pkg in parsed.packages) "present" else "absent"
    }
}

class AppOpProbe(private val transport: AdbTransport, private val userId: Int) {
    suspend fun read(pkg: String, op: String): String? {
        val raw = transport.tryExecute(ShellCommand.AppOpsGet(pkg, op, userId).render()) ?: return null
        if (raw.exitCode != 0) return null
        return ShellParsers.parseAppOpMode(raw.stdout, op)
    }
}
