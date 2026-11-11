package com.miftah.core.model

enum class ProtectionClass { NORMAL, RUNTIME, SIGNATURE, UNKNOWN }

data class PermissionEntry(val name: String, val granted: Boolean, val protection: ProtectionClass)

object Protection {
    /** Maps PermissionInfo.protectionLevel (base bits 0xF) to a coarse class. */
    fun classify(protectionLevel: Int?): ProtectionClass {
        if (protectionLevel == null) return ProtectionClass.UNKNOWN
        return when (protectionLevel and 0xF) {
            0 -> ProtectionClass.NORMAL
            1 -> ProtectionClass.RUNTIME
            2, 3 -> ProtectionClass.SIGNATURE
            else -> ProtectionClass.UNKNOWN
        }
    }
}
