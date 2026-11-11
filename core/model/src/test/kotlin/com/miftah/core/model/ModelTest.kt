package com.miftah.core.model

import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    private fun app(pkg: String, label: String, system: Boolean = false, enabled: Boolean = true) =
        AppInfo(pkg, label, "1", 1, system, false, enabled, 0, 0)

    private fun result(outcome: Outcome, verified: Boolean) = OperationResult(
        "id", "op", 0, AccessLevel.ADB, outcome, 0, "", "", null, null, verified,
        RollbackAvailability.UNAVAILABLE, null,
    )

    @Test fun successRequiresVerification() {
        assertTrue(result(Outcome.SUCCEEDED, true).isConfirmedSuccess)
        assertFalse(result(Outcome.SUCCEEDED, false).isConfirmedSuccess)
        assertFalse(result(Outcome.FAILED, true).isConfirmedSuccess)
    }

    @Test fun accessLevelOrdering() {
        assertTrue(AccessLevel.ROOT.satisfies(AccessLevel.ADB))
        assertFalse(AccessLevel.STANDARD.satisfies(AccessLevel.ADB))
    }

    @Test fun queryFiltersAndSorts() {
        val apps = listOf(app("b.pkg", "Beta"), app("a.pkg", "alpha"), app("s.pkg", "Sys", system = true), app("d.pkg", "Dis", enabled = false))
        assertEquals(listOf("alpha", "Beta", "Dis", "Sys"), AppQuery.apply(apps, "", AppFilter.ALL).map { it.label })
        assertEquals(listOf("Sys"), AppQuery.apply(apps, "", AppFilter.SYSTEM).map { it.label })
        assertEquals(listOf("Dis"), AppQuery.apply(apps, "", AppFilter.DISABLED).map { it.label })
        assertEquals(listOf("Beta"), AppQuery.apply(apps, "B.PKG", AppFilter.USER).map { it.label })
        assertTrue(AppQuery.apply(emptyList(), "x", AppFilter.ALL).isEmpty())
    }
}
