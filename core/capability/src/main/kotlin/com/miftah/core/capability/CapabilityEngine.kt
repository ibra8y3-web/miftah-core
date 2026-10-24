package com.miftah.core.capability

import com.miftah.core.model.*

/**
 * Computes the status of each capability from real inputs: the device API level, the access level
 * actually held, and the set of capabilities this build really implements.
 */
class CapabilityEngine(private val implemented: Set<CapabilityId> = DEFAULT_IMPLEMENTED) {

    fun evaluate(sdkInt: Int, level: AccessLevel): List<Capability> =
        CapabilityId.entries.map { Capability(it, statusFor(it, sdkInt, level)) }

    private fun statusFor(id: CapabilityId, sdkInt: Int, level: AccessLevel): CapabilityStatus {
        if (id == CapabilityId.ADB_WIRELESS_PAIRING && sdkInt < 30) {
            return CapabilityStatus.UnsupportedOnDevice(ReasonCode.REQUIRES_API_30)
        }
        if (id !in implemented) return CapabilityStatus.TemporarilyUnavailable(ReasonCode.NOT_IMPLEMENTED_IN_BUILD)
        val needed = requiredLevel(id)
        return if (level.satisfies(needed)) CapabilityStatus.Supported
        else CapabilityStatus.HigherPrivilegeRequired(needed, ReasonCode.REQUIRES_ADB_CONNECTION)
    }

    companion object {
        /** Everything with a real code path in this build. Extensions (IPC SDK) are not built. */
        val DEFAULT_IMPLEMENTED: Set<CapabilityId> = CapabilityId.entries.toSet() - CapabilityId.EXTENSIONS

        fun requiredLevel(id: CapabilityId): AccessLevel = when (id) {
            CapabilityId.LIST_PACKAGES, CapabilityId.OPEN_APP_INFO, CapabilityId.ADB_WIRELESS_PAIRING,
            CapabilityId.READ_PERMISSIONS, CapabilityId.DIAGNOSTICS, CapabilityId.SYSTEM_UNINSTALL_DIALOG,
            CapabilityId.EXTENSIONS -> AccessLevel.STANDARD
            else -> AccessLevel.ADB
        }
    }
}
