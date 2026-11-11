package com.miftah.core.capability

import com.miftah.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CapabilityEngineTest {
    private fun status(list: List<Capability>, id: CapabilityId) = list.first { it.id == id }.status

    @Test fun standardLevelSupportsOnlyStandardCapabilities() {
        val r = CapabilityEngine().evaluate(34, AccessLevel.STANDARD)
        assertEquals(CapabilityStatus.Supported, status(r, CapabilityId.LIST_PACKAGES))
        assertEquals(CapabilityStatus.Supported, status(r, CapabilityId.READ_PERMISSIONS))
        assertTrue(status(r, CapabilityId.FORCE_STOP) is CapabilityStatus.HigherPrivilegeRequired)
        assertTrue(status(r, CapabilityId.TERMINAL) is CapabilityStatus.HigherPrivilegeRequired)
    }

    @Test fun adbLevelUnlocksAdbCapabilities() {
        val r = CapabilityEngine().evaluate(34, AccessLevel.ADB)
        for (id in listOf(CapabilityId.FORCE_STOP, CapabilityId.DISABLE_PACKAGE, CapabilityId.UNINSTALL_FOR_USER,
            CapabilityId.GRANT_REVOKE_PERMISSION, CapabilityId.READ_APPOPS, CapabilityId.WRITE_APPOPS,
            CapabilityId.TERMINAL, CapabilityId.READ_LOGCAT, CapabilityId.INSTALL_EXISTING)) {
            assertEquals(id.name, CapabilityStatus.Supported, status(r, id))
        }
    }

    @Test fun extensionsAreReportedNotImplemented() {
        val r = CapabilityEngine().evaluate(34, AccessLevel.ADB)
        assertEquals(CapabilityStatus.TemporarilyUnavailable(ReasonCode.NOT_IMPLEMENTED_IN_BUILD), status(r, CapabilityId.EXTENSIONS))
    }

    @Test fun pairingUnsupportedBelowApi30() {
        val r = CapabilityEngine().evaluate(29, AccessLevel.STANDARD)
        assertEquals(CapabilityStatus.UnsupportedOnDevice(ReasonCode.REQUIRES_API_30), status(r, CapabilityId.ADB_WIRELESS_PAIRING))
    }

    @Test fun customImplementedSetIsHonoured() {
        val engine = CapabilityEngine(setOf(CapabilityId.FORCE_STOP))
        assertEquals(CapabilityStatus.TemporarilyUnavailable(ReasonCode.NOT_IMPLEMENTED_IN_BUILD), status(engine.evaluate(34, AccessLevel.ADB), CapabilityId.TERMINAL))
        assertEquals(CapabilityStatus.Supported, status(engine.evaluate(34, AccessLevel.ADB), CapabilityId.FORCE_STOP))
    }

    @Test fun everyCapabilityGetsExactlyOneStatus() {
        assertEquals(CapabilityId.entries.size, CapabilityEngine().evaluate(34, AccessLevel.STANDARD).size)
    }
}
