package com.miftah.core.capability

import com.miftah.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CapabilityEngineTest {
    private fun status(list: List<Capability>, id: CapabilityId) = list.first { it.id == id }.status

    @Test fun standardLevelOnlyListsImplementedBasics() {
        val r = CapabilityEngine().evaluate(34, AccessLevel.STANDARD)
        assertEquals(CapabilityStatus.Supported, status(r, CapabilityId.LIST_PACKAGES))
        assertEquals(CapabilityStatus.Supported, status(r, CapabilityId.OPEN_APP_INFO))
        assertEquals(
            CapabilityStatus.TemporarilyUnavailable(ReasonCode.NOT_IMPLEMENTED_IN_BUILD),
            status(r, CapabilityId.FORCE_STOP),
        )
    }

    @Test fun pairingUnsupportedBelowApi30() {
        val r = CapabilityEngine().evaluate(29, AccessLevel.STANDARD)
        assertEquals(CapabilityStatus.UnsupportedOnDevice(ReasonCode.REQUIRES_API_30), status(r, CapabilityId.ADB_WIRELESS_PAIRING))
    }

    @Test fun implementedAdbCapabilityNeedsAdbLevel() {
        val engine = CapabilityEngine(setOf(CapabilityId.FORCE_STOP))
        assertTrue(status(engine.evaluate(34, AccessLevel.STANDARD), CapabilityId.FORCE_STOP) is CapabilityStatus.HigherPrivilegeRequired)
        assertEquals(CapabilityStatus.Supported, status(engine.evaluate(34, AccessLevel.ADB), CapabilityId.FORCE_STOP))
    }

    @Test fun everyCapabilityGetsExactlyOneStatus() {
        assertEquals(CapabilityId.entries.size, CapabilityEngine().evaluate(34, AccessLevel.STANDARD).size)
    }

    @Test fun disableNeedsAdbLevelByDefault() {
        val e = CapabilityEngine()
        assertTrue(status(e.evaluate(34, AccessLevel.STANDARD), CapabilityId.DISABLE_PACKAGE) is CapabilityStatus.HigherPrivilegeRequired)
        assertEquals(CapabilityStatus.Supported, status(e.evaluate(34, AccessLevel.ADB), CapabilityId.DISABLE_PACKAGE))
    }
}
