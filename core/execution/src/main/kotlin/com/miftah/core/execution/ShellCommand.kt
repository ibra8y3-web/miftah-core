package com.miftah.core.execution

import com.miftah.core.model.RollbackAvailability

object Validators {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
    private val PROP = Regex("^[A-Za-z0-9._-]+$")
    fun requirePackage(s: String) = require(PACKAGE.matches(s)) { "Invalid package name" }
    fun requireProp(s: String) = require(PROP.matches(s)) { "Invalid property key" }
    fun requireUser(id: Int) = require(id >= 0) { "Invalid user id" }
}

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
}
