package com.miftah.core.model

import org.junit.Assert.*
import org.junit.Test

class PrivacyStartupTest {
    private fun f(cat: PrivacyCategory, system: Boolean = false) = PrivacyFinding(cat, "p", "P", system)

    @Test fun scoreWeightsAndLevels() {
        assertEquals(5, PrivacyScore.weight(f(PrivacyCategory.ACCESSIBILITY)))
        assertEquals(1, PrivacyScore.weight(f(PrivacyCategory.ACCESSIBILITY, system = true)))
        assertEquals(0, PrivacyScore.weight(f(PrivacyCategory.OVERLAY, system = true)))
        val list = listOf(f(PrivacyCategory.ACCESSIBILITY), f(PrivacyCategory.NOTIFICATION_LISTENER), f(PrivacyCategory.OVERLAY))
        assertEquals(10, PrivacyScore.score(list))
        assertEquals(RiskLevel.LOW, PrivacyScore.level(0))
        assertEquals(RiskLevel.MEDIUM, PrivacyScore.level(10))
        assertEquals(RiskLevel.HIGH, PrivacyScore.level(25))
    }

    @Test fun groupingSortsUserAppsFirstAndDedups() {
        val raw = listOf(
            RawReceiver("sys.a", "Alpha", true, "sys.a.R1", true),
            RawReceiver("usr.b", "Beta", false, "usr.b.R2", true),
            RawReceiver("usr.b", "Beta", false, "usr.b.R1", false),
            RawReceiver("usr.b", "Beta", false, "usr.b.R1", false),
            RawReceiver("usr.c", "alpha user", false, "usr.c.R", true),
        )
        val g = StartupGrouping.group(raw)
        assertEquals(listOf("usr.c", "usr.b", "sys.a"), g.map { it.pkg })
        assertEquals(listOf("usr.b.R1", "usr.b.R2"), g[1].components.map { it.className })
        assertFalse(g[1].components[0].enabled)
        assertTrue(StartupGrouping.group(emptyList()).isEmpty())
    }
}
