package com.miftah.core.model

import org.junit.Assert.*
import org.junit.Test

class ModelExtraTest {
    private fun result(stdout: String = "", previous: String? = null) = OperationResult(
        "id1", "disable-user", 1000, AccessLevel.ADB, Outcome.SUCCEEDED, 0, stdout, "", previous, "disabled", true,
        RollbackAvailability.AVAILABLE, null,
    )

    @Test fun removedFilterAndInstalledOnlyElsewhere() {
        val a = AppInfo("a.b", "A", "1", 1, false, false, true, 0, 0)
        val gone = AppInfo("c.d", "C", "1", 1, true, false, true, 0, 0, installed = false)
        assertEquals(listOf("A"), AppQuery.apply(listOf(a, gone), "", AppFilter.ALL).map { it.label })
        assertEquals(listOf("C"), AppQuery.apply(listOf(a, gone), "", AppFilter.REMOVED).map { it.label })
    }

    @Test fun protectionClassification() {
        assertEquals(ProtectionClass.NORMAL, Protection.classify(0))
        assertEquals(ProtectionClass.RUNTIME, Protection.classify(1))
        assertEquals(ProtectionClass.SIGNATURE, Protection.classify(2 or 0x10))
        assertEquals(ProtectionClass.UNKNOWN, Protection.classify(null))
    }

    @Test fun jsonEscapesAndStructure() {
        val json = AuditExport.toJson(listOf(AuditEntry("com.x\"y", result(stdout = "line1\nline2\t\\"))))
        assertTrue(json.startsWith("{\"version\":1,\"entries\":[{"))
        assertTrue(json.contains("\"target\":\"com.x\\\"y\""))
        assertTrue(json.contains("line1\\nline2\\t\\\\"))
        assertTrue(json.contains("\"exitCode\":0"))
        assertEquals("{\"version\":1,\"entries\":[]}", AuditExport.toJson(emptyList()))
    }

    @Test fun csvQuotesAndNeutralisesFormulas() {
        assertEquals("plain", AuditExport.csvCell("plain"))
        assertEquals("\"a,b\"", AuditExport.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", AuditExport.csvCell("say \"hi\""))
        assertEquals("'=1+1", AuditExport.csvCell("=1+1"))
        val csv = AuditExport.toCsv(listOf(AuditEntry("t", result())))
        assertEquals(2, csv.trim().lines().size)
    }

    @Test fun batteryPercentAndReport() {
        assertEquals(50, BatterySnapshot.percentOf(50, 100))
        assertNull(BatterySnapshot.percentOf(-1, 100))
        assertNull(BatterySnapshot.percentOf(5, 0))
        val text = DiagnosticsReport.toText(DiagnosticsSnapshot("M", "X", "14", 34, listOf("arm64-v8a"), null, null, null, 10, 1, 2, emptyList(), 5))
        assertTrue(text.contains("Storage: not readable"))
        assertTrue(text.contains("10 installed, 1 disabled, 2 removed"))
        assertTrue(text.contains("none"))
    }
}
