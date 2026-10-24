package com.miftah.core.execution

import com.miftah.core.model.AccessLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Test-only fake. */
private class CommandTransport(val handler: suspend (String) -> RawShellResult) : AdbTransport {
    override val accessLevel = AccessLevel.ADB
    val log = mutableListOf<String>()
    override suspend fun execute(command: String): RawShellResult { log += command; return handler(command) }
}

private fun disableFake(failFor: String? = null): CommandTransport {
    val disabled = mutableSetOf<String>()
    return CommandTransport { cmd ->
        when {
            cmd.startsWith("pm disable-user") -> {
                val pkg = cmd.substringAfterLast(' ')
                if (pkg == failFor) RawShellResult(1, "", "denied") else { disabled += pkg; RawShellResult(0, "", "") }
            }
            cmd.startsWith("pm enable") -> { disabled -= cmd.substringAfterLast(' '); RawShellResult(0, "", "") }
            cmd.startsWith("pm list packages -d") -> RawShellResult(0, disabled.joinToString("") { "package:$it\n" }, "")
            else -> RawShellResult(1, "", "unexpected: $cmd")
        }
    }
}

class Phase2Test {
    @Test fun newCommandsRenderAndValidate() {
        assertEquals("pm grant --user 0 com.a android.permission.CAMERA", ShellCommand.GrantPermission("com.a", "android.permission.CAMERA", 0).render())
        assertEquals("pm revoke --user 0 com.a android.permission.CAMERA", ShellCommand.RevokePermission("com.a", "android.permission.CAMERA", 0).render())
        assertEquals("cmd appops set --user 0 com.a RUN_IN_BACKGROUND ignore", ShellCommand.AppOpsSet("com.a", "RUN_IN_BACKGROUND", "ignore", 0).render())
        assertEquals("cmd appops get --user 0 com.a RUN_IN_BACKGROUND", ShellCommand.AppOpsGet("com.a", "RUN_IN_BACKGROUND", 0).render())
        assertEquals("pm install-existing --user 0 com.a", ShellCommand.InstallExisting("com.a", 0).render())
        assertEquals("pidof com.a", ShellCommand.PidOf("com.a").render())
        assertEquals("logcat -d -t 50", ShellCommand.Logcat(50).render())
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.AppOpsSet("com.a", "run;x", "allow", 0) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.AppOpsSet("com.a", "OP", "nuke", 0) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.GrantPermission("com.a", "x y", 0) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.Logcat(0) }
    }

    @Test fun rollbackOnlyWhenPreviousStateProvesAChange() {
        val d = ShellCommand.DisableUser("com.a", 0)
        assertEquals(ShellCommand.Enable("com.a", 0), Rollback.inverse(d, "enabled"))
        assertNull(Rollback.inverse(d, "disabled"))
        assertNull(Rollback.inverse(d, null))
        assertEquals(ShellCommand.InstallExisting("com.a", 0), Rollback.inverse(ShellCommand.UninstallForUser("com.a", 0), "present"))
        assertEquals(ShellCommand.RevokePermission("com.a", "p.Q", 0), Rollback.inverse(ShellCommand.GrantPermission("com.a", "p.Q", 0), "denied"))
        assertEquals(ShellCommand.AppOpsSet("com.a", "OP", "allow", 0), Rollback.inverse(ShellCommand.AppOpsSet("com.a", "OP", "ignore", 0), "allow"))
        assertNull(Rollback.inverse(ShellCommand.ForceStop("com.a"), "running"))
    }

    @Test fun appOpsParsing() {
        assertEquals("allow", ShellParsers.parseAppOpMode("RUN_IN_BACKGROUND: allow; time=+1d", "RUN_IN_BACKGROUND"))
        assertEquals("ignore", ShellParsers.parseAppOpMode("Uid mode: RUN_IN_BACKGROUND: ignore", "RUN_IN_BACKGROUND"))
        assertNull(ShellParsers.parseAppOpMode("No operations.", "RUN_IN_BACKGROUND"))
        assertNull(ShellParsers.parseAppOpMode("", "X"))
    }

    @Test fun commandRisk() {
        assertEquals(Risk.DESTRUCTIVE, CommandRisk.classify("pm uninstall --user 0 com.a"))
        assertEquals(Risk.DESTRUCTIVE, CommandRisk.classify("rm -rf /sdcard/x"))
        assertEquals(Risk.DESTRUCTIVE, CommandRisk.classify("echo hi > /sdcard/a"))
        assertEquals(Risk.DESTRUCTIVE, CommandRisk.classify("settings put global x 1"))
        assertEquals(Risk.NORMAL, CommandRisk.classify("getprop ro.build.version.release"))
        assertEquals(Risk.NORMAL, CommandRisk.classify("ls /sdcard/rm"))
        assertTrue(CommandRisk.isAcceptable("id"))
        assertFalse(CommandRisk.isAcceptable(""))
        assertFalse(CommandRisk.isAcceptable("a\nb"))
        assertFalse(CommandRisk.isAcceptable("x".repeat(3000)))
    }

    private fun steps(t: CommandTransport, vararg pkgs: String): List<PlanStep> {
        val v = Verifiers(t, 0)
        return pkgs.mapIndexed { i, p ->
            val c = ShellCommand.DisableUser(p, 0)
            PlanStep(i, p, c, v.forCommand(c))
        }
    }

    @Test fun planAllStepsVerified() = runTest {
        val t = disableFake()
        val runner = PlanRunner(ExecutionEngine(t)) { true }
        val r = runner.run(steps(t, "com.a", "com.b"), { _, _, _ -> true })
        assertEquals(listOf(StepStatus.CONFIRMED, StepStatus.CONFIRMED), r.outcomes.map { it.status })
        assertNull(r.stoppedReason)
        assertEquals("enabled", r.outcomes[0].result?.previousState)
        assertEquals(ShellCommand.Enable("com.a", 0), Rollback.inverse(r.outcomes[0].step.command, r.outcomes[0].result?.previousState))
    }

    @Test fun planStopsOnFailure() = runTest {
        val t = disableFake(failFor = "com.b")
        val r = PlanRunner(ExecutionEngine(t)) { true }.run(steps(t, "com.a", "com.b", "com.c"), { _, _, _ -> true })
        assertEquals(listOf(StepStatus.CONFIRMED, StepStatus.FAILED, StepStatus.SKIPPED), r.outcomes.map { it.status })
        assertEquals(StopReason.STEP_FAILED, r.stoppedReason)
        assertTrue(t.log.none { it.contains("com.c") })
    }

    @Test fun planStopsWhenApprovalDeclined() = runTest {
        val t = disableFake()
        var asked = 0
        val r = PlanRunner(ExecutionEngine(t)) { true }.run(steps(t, "com.a", "com.b", "com.c"), { _, _, _ -> asked++; asked < 2 })
        assertEquals(listOf(StepStatus.CONFIRMED, StepStatus.REJECTED, StepStatus.SKIPPED), r.outcomes.map { it.status })
        assertEquals(StopReason.APPROVAL_DECLINED, r.stoppedReason)
        assertEquals(2, asked)
    }

    @Test fun planStopsWhenDisconnected() = runTest {
        val t = disableFake()
        val r = PlanRunner(ExecutionEngine(t)) { false }.run(steps(t, "com.a", "com.b"), { _, _, _ -> true })
        assertEquals(listOf(StepStatus.SKIPPED, StepStatus.SKIPPED), r.outcomes.map { it.status })
        assertEquals(StopReason.DISCONNECTED, r.stoppedReason)
        assertTrue(t.log.isEmpty())
    }

    @Test fun terminalRunner() = runTest {
        val ok = CommandTransport { RawShellResult(0, "hello token=abc\n", "") }
        val e = TerminalRunner(ok).run("echo hello")
        assertEquals(0, e.exitCode)
        assertFalse(e.stdout.contains("abc"))
        val bad = CommandTransport { RawShellResult(0, "", "") }
        val inv = TerminalRunner(bad).run("a\nb")
        assertNotNull(inv.error)
        assertTrue(bad.log.isEmpty())
        val slow = CommandTransport { delay(60_000); RawShellResult(0, "", "") }
        assertTrue(TerminalRunner(slow).run("sleep 99", timeoutMs = 500).timedOut)
        val broken = CommandTransport { throw TransportException("lost") }
        assertEquals("lost", TerminalRunner(broken).run("id").error)
    }
}
