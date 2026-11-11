package com.miftah.core.execution

import com.miftah.core.model.AccessLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class ScriptedTransport(private val result: () -> RawShellResult) : AdbTransport {
    override val accessLevel = AccessLevel.ADB
    val commands = mutableListOf<String>()
    override suspend fun execute(command: String): RawShellResult { commands += command; return result() }
}

class ProbeAndMarkerTest {
    @Test fun wrapAndParseRoundTrip() {
        assertEquals("id; echo \"__MIFTAH_EXIT_abc123:\$?\"", ExitMarker.wrap("id", "abc123"))
        val r0 = ExitMarker.parse("uid=2000(shell)\n__MIFTAH_EXIT_abc123:0\n", "abc123")
        assertEquals(0, r0.exitCode); assertEquals("uid=2000(shell)", r0.stdout)
        assertEquals(1, ExitMarker.parse("err\n__MIFTAH_EXIT_abc123:1\n", "abc123").exitCode)
    }

    @Test fun missingOrSpoofedMarkerGivesNoExitCode() {
        assertNull(ExitMarker.parse("output only", "abc123").exitCode)
        assertNull(ExitMarker.parse("__MIFTAH_EXIT_other:0\n", "abc123").exitCode)
        assertNull(ExitMarker.parse("__MIFTAH_EXIT_abc123:x\n", "abc123").exitCode)
        assertThrows(IllegalArgumentException::class.java) { ExitMarker.wrap("id", "a b;") }
    }

    @Test fun probeReportsDisabledEnabledAndUnknown() = runTest {
        val disabled = ScriptedTransport { RawShellResult(0, "package:com.a\npackage:com.b\n", "") }
        val probe = PackageStateProbe(disabled, 0)
        assertEquals("disabled", probe.read("com.a"))
        assertEquals("enabled", probe.read("com.zzz"))
        assertEquals("pm list packages -d --user 0", disabled.commands.first())
        assertNull(PackageStateProbe(ScriptedTransport { RawShellResult(1, "", "") }, 0).read("com.a"))
        assertNull(PackageStateProbe(ScriptedTransport { RawShellResult(0, "WARNING junk\n", "") }, 0).read("com.a"))
        assertNull(PackageStateProbe(ScriptedTransport { throw TransportException("lost") }, 0).read("com.a"))
    }
}
