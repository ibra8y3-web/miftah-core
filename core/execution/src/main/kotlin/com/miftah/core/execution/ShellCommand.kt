package com.miftah.core.execution

import com.miftah.core.model.RollbackAvailability

object Validators {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
    private val PROP = Regex("^[A-Za-z0-9._-]+$")
    private val PERMISSION = Regex("^[A-Za-z][A-Za-z0-9_.]*$")
    private val APPOP = Regex("^[A-Z][A-Z0-9_]*$")
    val APPOP_MODES = setOf("allow", "ignore", "deny", "default", "foreground")
    private val COMPONENT = Regex("^[A-Za-z0-9_.\\$]{1,300}$")
    private val SCALE = Regex("^[0-9]{1,2}(\\.[0-9]{1,2})?$")
    val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

    fun requirePackage(s: String) = require(PACKAGE.matches(s)) { "Invalid package name" }
    fun requireProp(s: String) = require(PROP.matches(s)) { "Invalid property key" }
    fun requireUser(id: Int) = require(id >= 0) { "Invalid user id" }
    fun requirePermission(s: String) = require(PERMISSION.matches(s)) { "Invalid permission name" }
    fun requireAppOp(s: String) = require(APPOP.matches(s)) { "Invalid AppOps operation" }
    fun requireComponent(s: String) = require(COMPONENT.matches(s)) { "Invalid component name" }
    fun requireDensity(d: Int) = require(d in 100..1000) { "Invalid density" }
    fun requireAnimKey(s: String) = require(s in ANIM_KEYS) { "Invalid animation setting" }
    fun requireScale(s: String) = require(SCALE.matches(s) && s.toDouble() <= 10.0) { "Invalid scale" }
    fun requireAppOpMode(s: String) = require(s in APPOP_MODES) { "Invalid AppOps mode" }
}

/** Operations the UI offers for AppOps; kept small and well known. */
val KnownAppOps = listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "SYSTEM_ALERT_WINDOW", "GET_USAGE_STATS", "REQUEST_INSTALL_PACKAGES")

/** Allow-list of typed commands. Arguments are validated, so no free-form shell text can be injected. */
sealed class ShellCommand(
    val name: String,
    val destructive: Boolean,
    val rollback: RollbackAvailability,
    val rollbackNote: String?,
) {
    abstract fun render(): String

    data object Identity : ShellCommand("identity", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        override fun render() = "id"
    }

    data class GetProp(val key: String) : ShellCommand("getprop", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        init { Validators.requireProp(key) }
        override fun render() = "getprop $key"
    }

    data class ListPackages(val userId: Int? = null, val onlyDisabled: Boolean = false) :
        ShellCommand("list-packages", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        init { userId?.let(Validators::requireUser) }
        override fun render() = buildString {
            append("pm list packages")
            if (onlyDisabled) append(" -d")
            if (userId != null) append(" --user $userId")
        }
    }

    data class PidOf(val pkg: String) : ShellCommand("pidof", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        init { Validators.requirePackage(pkg) }
        override fun render() = "pidof $pkg"
    }

    data class Logcat(val lines: Int = 200) : ShellCommand("logcat", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        init { require(lines in 1..2000) { "Invalid line count" } }
        override fun render() = "logcat -d -t $lines"
    }

    data class AppOpsGet(val pkg: String, val op: String, val userId: Int) :
        ShellCommand("appops-get", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        init { Validators.requirePackage(pkg); Validators.requireAppOp(op); Validators.requireUser(userId) }
        override fun render() = "cmd appops get --user $userId $pkg $op"
    }

    data class ForceStop(val pkg: String) :
        ShellCommand("force-stop", true, RollbackAvailability.UNAVAILABLE, "process stop cannot be undone") {
        init { Validators.requirePackage(pkg) }
        override fun render() = "am force-stop $pkg"
    }

    data class DisableUser(val pkg: String, val userId: Int) :
        ShellCommand("disable-user", true, RollbackAvailability.AVAILABLE, "re-enable with pm enable") {
        init { Validators.requirePackage(pkg); Validators.requireUser(userId) }
        override fun render() = "pm disable-user --user $userId $pkg"
    }

    data class Enable(val pkg: String, val userId: Int) :
        ShellCommand("enable", false, RollbackAvailability.AVAILABLE, "disable again with pm disable-user") {
        init { Validators.requirePackage(pkg); Validators.requireUser(userId) }
        override fun render() = "pm enable --user $userId $pkg"
    }

    data class UninstallForUser(val pkg: String, val userId: Int) :
        ShellCommand("uninstall-for-user", true, RollbackAvailability.PARTIAL,
            "may be restorable with install-existing only if the APK still exists on the system partition") {
        init { Validators.requirePackage(pkg); Validators.requireUser(userId) }
        override fun render() = "pm uninstall --user $userId $pkg"
    }

    data class InstallExisting(val pkg: String, val userId: Int) :
        ShellCommand("install-existing", false, RollbackAvailability.PARTIAL, "remove again with pm uninstall --user") {
        init { Validators.requirePackage(pkg); Validators.requireUser(userId) }
        override fun render() = "pm install-existing --user $userId $pkg"
    }

    data class GrantPermission(val pkg: String, val permission: String, val userId: Int) :
        ShellCommand("grant-permission", true, RollbackAvailability.AVAILABLE, "revoke again") {
        init { Validators.requirePackage(pkg); Validators.requirePermission(permission); Validators.requireUser(userId) }
        override fun render() = "pm grant --user $userId $pkg $permission"
    }

    data class RevokePermission(val pkg: String, val permission: String, val userId: Int) :
        ShellCommand("revoke-permission", true, RollbackAvailability.AVAILABLE, "grant again") {
        init { Validators.requirePackage(pkg); Validators.requirePermission(permission); Validators.requireUser(userId) }
        override fun render() = "pm revoke --user $userId $pkg $permission"
    }

    data class AppOpsSet(val pkg: String, val op: String, val mode: String, val userId: Int) :
        ShellCommand("appops-set", true, RollbackAvailability.AVAILABLE, "restore the previous mode") {
        init {
            Validators.requirePackage(pkg); Validators.requireAppOp(op)
            Validators.requireAppOpMode(mode); Validators.requireUser(userId)
        }
        override fun render() = "cmd appops set --user $userId $pkg $op $mode"
    }

    data class DisableComponent(val pkg: String, val component: String, val userId: Int) :
        ShellCommand("disable-component", true, RollbackAvailability.AVAILABLE, "re-enable with pm enable") {
        init { Validators.requirePackage(pkg); Validators.requireComponent(component); Validators.requireUser(userId) }
        override fun render() = "pm disable --user $userId '$pkg/$component'"
    }

    data class EnableComponent(val pkg: String, val component: String, val userId: Int) :
        ShellCommand("enable-component", false, RollbackAvailability.AVAILABLE, "disable again with pm disable") {
        init { Validators.requirePackage(pkg); Validators.requireComponent(component); Validators.requireUser(userId) }
        override fun render() = "pm enable --user $userId '$pkg/$component'"
    }

    data class WmDensity(val dpi: Int) :
        ShellCommand("wm-density", true, RollbackAvailability.AVAILABLE, "reset or restore the previous density") {
        init { Validators.requireDensity(dpi) }
        override fun render() = "wm density $dpi"
    }

    data object WmDensityReset : ShellCommand("wm-density-reset", false, RollbackAvailability.AVAILABLE, "set a density again") {
        override fun render() = "wm density reset"
    }

    data object WmDensityQuery : ShellCommand("wm-density-query", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        override fun render() = "wm density"
    }

    data class SettingsPutGlobal(val key: String, val value: String) :
        ShellCommand("settings-put-global", false, RollbackAvailability.AVAILABLE, "restore the previous value") {
        init { Validators.requireAnimKey(key); Validators.requireScale(value) }
        override fun render() = "settings put global $key $value"
    }

    data class SettingsGetGlobal(val key: String) :
        ShellCommand("settings-get-global", false, RollbackAvailability.UNAVAILABLE, "read-only") {
        init { Validators.requireAnimKey(key) }
        override fun render() = "settings get global $key"
    }
}
