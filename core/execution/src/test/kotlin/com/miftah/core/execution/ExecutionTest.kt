package com.miftah.core.execution

import com.miftah.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Test-only fake; never part of production code. */
private class FakeTransport(
    override val accessLevel: AccessLevel = AccessLevel.ADB,
    val response: suspend () -> RawShellResult,
) : AdbTransport {
    val commands = mutableListOf<String>()
    override suspend fun execute(command: String): RawShellResult { commands += command; return response() }
}

class ExecutionTest {
    private val ok = RawShellResult(0, "", "")

    @Test fun rendersAndRejectsInjection() {
        assertEquals("am force-stop com.example.app", ShellCommand.ForceStop("com.example.app").render())
        assertEquals("pm list packages -d --user 0", ShellCommand.ListPackages(0, true).render())
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.ForceStop("com.x; rm -rf /") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.GetProp("a b") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.DisableUser("com.x", -1) }
    }

    @Test fun destructiveNeedsConfirmationAndDoesNotTouchTransport() = runTest {
        val t = FakeTransport { ok }
        val r = ExecutionEngine(t).run(ShellCommand.ForceStop("com.example.app"))
        assertEquals(Outcome.REJECTED, r.outcome)
        assertTrue(t.commands.isEmpty())
    }

    @Test fun exitZeroWithoutVerificationIsNotConfirmed() = runTest {
        val r = ExecutionEngine(FakeTransport { ok }).run(ShellCommand.Identity)
        assertEquals(Outcome.SUCCEEDED, r.outcome)
        assertFalse(r.isConfirmedSuccess)
    }

    @Test fun verificationConfirmsOrDenies() = runTest {
        fun verification(afterValue: String): Verification {
            val values = ArrayDeque(listOf("enabled", afterValue))
            return Verification({ values.removeFirst() }) { _, after -> after == "disabled" }
        }
        val cmd = ShellCommand.DisableUser("com.example.app", 0)
        val good = ExecutionEngine(FakeTransport { ok }).run(cmd, confirmed = true, verification = verification("disabled"))
        assertTrue(good.isConfirmedSuccess)
        assertEquals("enabled", good.previousState)
        assertEquals("disabled", good.verifiedState)
        val bad = ExecutionEngine(FakeTransport { ok }).run(cmd, confirmed = true, verification = verification("enabled"))
        assertFalse(bad.isConfirmedSuccess)
    }

    @Test fun nonZeroExitIsFailureAndSecretsAreRedacted() = runTest {
        val t = FakeTransport { RawShellResult(1, "", "denied token=abc123") }
        val r = ExecutionEngine(t).run(ShellCommand.Identity)
        assertEquals(Outcome.FAILED, r.outcome)
        assertEquals(1, r.exitCode)
        assertFalse(r.stderr.contains("abc123"))
    }

    @Test fun timeoutAndTransportFailure() = runTest {
        val slow = FakeTransport { delay(60_000); ok }
        assertEquals(Outcome.TIMED_OUT, ExecutionEngine(slow).run(ShellCommand.Identity, timeoutMs = 1_000).outcome)
        val broken = FakeTransport { throw TransportException("connection lost") }
        val r = ExecutionEngine(broken).run(ShellCommand.Identity)
        assertEquals(Outcome.FAILED, r.outcome)
        assertTrue(r.stderr.contains("connection lost"))
    }

    @Test fun parsers() {
        val p = ShellParsers.parsePackageList("package:com.a\n\npackage:com.b\ngarbage line\npackage:\n")
        assertEquals(setOf("com.a", "com.b"), p.packages)
        assertEquals(2, p.malformedLines)
        assertEquals(ShellIdentity(2000, "shell"), ShellParsers.parseIdentity("uid=2000(shell) gid=2000(shell) groups=1004"))
        assertNull(ShellParsers.parseIdentity(""))
        assertNull(ShellParsers.parseIdentity("garbled"))
    }

    @Test fun redactorKeepsNormalText() {
        assertEquals("hello world", Redactor.redact("hello world"))
        assertEquals("pairing code: <redacted>", Redactor.redact("pairing code: 123456"))
    }
}
