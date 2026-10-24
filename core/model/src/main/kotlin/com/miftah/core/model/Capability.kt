package com.miftah.core.model

/** Effective access level the app currently holds. Ordered from least to most privileged. */
enum class AccessLevel { STANDARD, ADB, ROOT, PRIVILEGED;
    fun satisfies(required: AccessLevel): Boolean = ordinal >= required.ordinal
}

enum class CapabilityId {
    LIST_PACKAGES, OPEN_APP_INFO, ADB_WIRELESS_PAIRING, FORCE_STOP, DISABLE_PACKAGE,
    UNINSTALL_FOR_USER, GRANT_REVOKE_PERMISSION, READ_APPOPS, TERMINAL, READ_LOGCAT,
    READ_PERMISSIONS, WRITE_APPOPS, INSTALL_EXISTING, DIAGNOSTICS, SYSTEM_UNINSTALL_DIALOG, EXTENSIONS
}

/** Machine-readable reasons; the UI maps each to a localized string. */
enum class ReasonCode { NOT_IMPLEMENTED_IN_BUILD, REQUIRES_ADB_CONNECTION, REQUIRES_API_30 }

sealed interface CapabilityStatus {
    data object Supported : CapabilityStatus
    data class PermissionRequired(val reason: ReasonCode) : CapabilityStatus
    data class HigherPrivilegeRequired(val required: AccessLevel, val reason: ReasonCode) : CapabilityStatus
    data class UnsupportedOnDevice(val reason: ReasonCode) : CapabilityStatus
    data class TemporarilyUnavailable(val reason: ReasonCode) : CapabilityStatus
    data class Failed(val reason: ReasonCode) : CapabilityStatus
}

data class Capability(val id: CapabilityId, val status: CapabilityStatus)
