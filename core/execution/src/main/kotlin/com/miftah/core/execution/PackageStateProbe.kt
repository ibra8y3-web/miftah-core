package com.miftah.core.execution

/** Reads whether a package is disabled for [userId] by asking the system, independent of the command that changed it. */
class PackageStateProbe(private val transport: AdbTransport, private val userId: Int) {
    suspend fun read(pkg: String): String? {
        Validators.requirePackage(pkg)
        val raw = try {
            transport.execute(ShellCommand.ListPackages(userId, onlyDisabled = true).render())
        } catch (e: TransportException) {
            return null
        }
        if (raw.exitCode != 0) return null
        val parsed = ShellParsers.parsePackageList(raw.stdout)
        if (parsed.malformedLines > 0) return null // unreliable output: refuse to conclude anything
        return if (pkg in parsed.packages) STATE_DISABLED else STATE_ENABLED
    }

    companion object {
        const val STATE_DISABLED = "disabled"
        const val STATE_ENABLED = "enabled"
    }
}

object Verifications {
    fun disabled(probe: PackageStateProbe, pkg: String) =
        Verification({ probe.read(pkg) }) { _, after -> after == PackageStateProbe.STATE_DISABLED }

    fun enabled(probe: PackageStateProbe, pkg: String) =
        Verification({ probe.read(pkg) }) { _, after -> after == PackageStateProbe.STATE_ENABLED }
}
