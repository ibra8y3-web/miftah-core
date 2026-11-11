package com.miftah.core.execution

/** Builds the independent before/after check for each command that changes state. */
class Verifiers(
    private val transport: AdbTransport,
    private val userId: Int,
    /** Reads "granted"/"denied" from the platform (PackageManager) for grant/revoke verification. */
    private val permissionState: (suspend (String, String) -> String?)? = null,
    /** Reads "enabled"/"disabled" for pkg + component class from the platform. */
    private val componentState: (suspend (String, String) -> String?)? = null,
) {
    fun forCommand(cmd: ShellCommand): Verification? = when (cmd) {
        is ShellCommand.DisableUser -> Verifications.disabled(PackageStateProbe(transport, userId), cmd.pkg)
        is ShellCommand.Enable -> Verifications.enabled(PackageStateProbe(transport, userId), cmd.pkg)
        is ShellCommand.ForceStop -> {
            val p = ProcessStateProbe(transport)
            Verification({ p.read(cmd.pkg) }) { _, after -> after == "stopped" }
        }
        is ShellCommand.UninstallForUser -> {
            val p = PackagePresenceProbe(transport, userId)
            Verification({ p.read(cmd.pkg) }) { _, after -> after == "absent" }
        }
        is ShellCommand.InstallExisting -> {
            val p = PackagePresenceProbe(transport, userId)
            Verification({ p.read(cmd.pkg) }) { _, after -> after == "present" }
        }
        is ShellCommand.GrantPermission -> permissionState?.let { ps ->
            Verification({ ps(cmd.pkg, cmd.permission) }) { _, after -> after == "granted" }
        }
        is ShellCommand.RevokePermission -> permissionState?.let { ps ->
            Verification({ ps(cmd.pkg, cmd.permission) }) { _, after -> after == "denied" }
        }
        is ShellCommand.AppOpsSet -> {
            val p = AppOpProbe(transport, userId)
            Verification({ p.read(cmd.pkg, cmd.op) }) { _, after -> after == cmd.mode }
        }
        is ShellCommand.DisableComponent -> componentState?.let { cs ->
            Verification({ cs(cmd.pkg, cmd.component) }) { _, after -> after == "disabled" }
        }
        is ShellCommand.EnableComponent -> componentState?.let { cs ->
            Verification({ cs(cmd.pkg, cmd.component) }) { _, after -> after == "enabled" }
        }
        is ShellCommand.WmDensity -> {
            val p = DensityProbe(transport)
            Verification({ p.read() }) { _, after -> after == "dpi:${cmd.dpi}" }
        }
        is ShellCommand.WmDensityReset -> {
            val p = DensityProbe(transport)
            Verification({ p.read() }) { _, after -> after == "default" }
        }
        is ShellCommand.SettingsPutGlobal -> {
            val p = AnimScaleProbe(transport, cmd.key)
            val want = ShellParsers.normalizeScale(cmd.value)
            Verification({ p.read() }) { _, after -> after != null && after == want }
        }
        else -> null
    }
}

/** Computes the inverse command, only when the state read BEFORE the operation proves it was a real change. */
object Rollback {
    fun inverse(cmd: ShellCommand, previousState: String?): ShellCommand? = when (cmd) {
        is ShellCommand.DisableUser -> if (previousState == "enabled") ShellCommand.Enable(cmd.pkg, cmd.userId) else null
        is ShellCommand.Enable -> if (previousState == "disabled") ShellCommand.DisableUser(cmd.pkg, cmd.userId) else null
        is ShellCommand.UninstallForUser -> if (previousState == "present") ShellCommand.InstallExisting(cmd.pkg, cmd.userId) else null
        is ShellCommand.InstallExisting -> if (previousState == "absent") ShellCommand.UninstallForUser(cmd.pkg, cmd.userId) else null
        is ShellCommand.GrantPermission -> if (previousState == "denied") ShellCommand.RevokePermission(cmd.pkg, cmd.permission, cmd.userId) else null
        is ShellCommand.RevokePermission -> if (previousState == "granted") ShellCommand.GrantPermission(cmd.pkg, cmd.permission, cmd.userId) else null
        is ShellCommand.AppOpsSet ->
            if (previousState != null && previousState in Validators.APPOP_MODES && previousState != cmd.mode)
                ShellCommand.AppOpsSet(cmd.pkg, cmd.op, previousState, cmd.userId) else null
        is ShellCommand.DisableComponent -> if (previousState == "enabled") ShellCommand.EnableComponent(cmd.pkg, cmd.component, cmd.userId) else null
        is ShellCommand.EnableComponent -> if (previousState == "disabled") ShellCommand.DisableComponent(cmd.pkg, cmd.component, cmd.userId) else null
        is ShellCommand.WmDensity, is ShellCommand.WmDensityReset -> densityInverse(previousState)
        is ShellCommand.SettingsPutGlobal -> when {
            previousState == "default" -> ShellCommand.SettingsPutGlobal(cmd.key, "1.0")
            previousState != null && previousState != ShellParsers.normalizeScale(cmd.value) && previousState.toDoubleOrNull() != null ->
                runCatching { ShellCommand.SettingsPutGlobal(cmd.key, previousState) }.getOrNull()
            else -> null
        }
        else -> null
    }

    private fun densityInverse(previous: String?): ShellCommand? = when {
        previous == "default" -> ShellCommand.WmDensityReset
        previous != null && previous.startsWith("dpi:") ->
            previous.removePrefix("dpi:").toIntOrNull()?.let { runCatching { ShellCommand.WmDensity(it) }.getOrNull() }
        else -> null
    }
}
