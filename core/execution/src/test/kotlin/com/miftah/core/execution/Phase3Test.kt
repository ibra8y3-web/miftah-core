package com.miftah.core.execution

import com.miftah.core.model.AccessLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class DisplayTransport(var override: Int? = null, val physical: Int = 420) : AdbTransport {
    override val accessLevel = AccessLevel.ADB
    var scale: String? = null
    override suspend fun execute(command: String): RawShellResult = when {
        command == "wm density" -> RawShellResult(0, "Physical density: $physical\n" + (override?.let { "Override density: $it\n" } ?: ""), "")
        command.startsWith("wm density reset") -> { override = null; RawShellResult(0, "", "") }
        command.startsWith("wm density ") -> { override = command.substringAfterLast(' ').toInt(); RawShellResult(0, "", "") }
        command.startsWith("settings get global") -> RawShellResult(0, (scale ?: "null") + "\n", "")
        command.startsWith("settings put global") -> { scale = command.substringAfterLast(' '); RawShellResult(0, "", "") }
        else -> RawShellResult(1, "", "unexpected: $command")
    }
}

class Phase3Test {
    @Test fun componentCommandsRenderQuotedAndValidate() {
        assertEquals("pm disable --user 0 'com.a/com.a.Boot\$Receiver'", ShellCommand.DisableComponent("com.a", "com.a.Boot\$Receiver", 0).render())
        assertEquals("pm enable --user 0 'com.a/.R'", ShellCommand.EnableComponent("com.a", ".R", 0).render())
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.DisableComponent("com.a", "x';rm -rf /;'", 0) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.DisableComponent("com.a", "", 0) }
    }

    @Test fun displayCommandsValidate() {
        assertEquals("wm density 360", ShellCommand.WmDensity(360).render())
        assertEquals("settings put global window_animation_scale 0.5", ShellCommand.SettingsPutGlobal("window_animation_scale", "0.5").render())
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.WmDensity(20) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.WmDensity(5000) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.SettingsPutGlobal("bogus", "1") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.SettingsPutGlobal("window_animation_scale", "1;reboot") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.SettingsPutGlobal("window_animation_scale", "99") }
    }

    @Test fun rollbackRules() {
        val d = ShellCommand.DisableComponent("com.a", ".R", 0)
        assertEquals(ShellCommand.EnableComponent("com.a", ".R", 0), Rollback.inverse(d, "enabled"))
        assertNull(Rollback.inverse(d, "disabled"))
        assertEquals(ShellCommand.WmDensityReset, Rollback.inverse(ShellCommand.WmDensity(300), "default"))
        assertEquals(ShellCommand.WmDensity(400), Rollback.inverse(ShellCommand.WmDensity(300), "dpi:400"))
        assertNull(Rollback.inverse(ShellCommand.WmDensity(300), null))
        assertEquals(ShellCommand.SettingsPutGlobal("window_animation_scale", "1.0"),
            Rollback.inverse(ShellCommand.SettingsPutGlobal("window_animation_scale", "0.5"), "default"))
        assertEquals(ShellCommand.SettingsPutGlobal("window_animation_scale", "1.5"),
            Rollback.inverse(ShellCommand.SettingsPutGlobal("window_animation_scale", "0.5"), "1.5"))
    }

    @Test fun parsers() {
        assertEquals(DensityInfo(420, null), ShellParsers.parseDensity("Physical density: 420\n"))
        assertEquals(DensityInfo(420, 360), ShellParsers.parseDensity("Physical density: 420\nOverride density: 360"))
        assertNull(ShellParsers.parseDensity("garbage"))
        assertEquals("0.5", ShellParsers.normalizeScale("0.50\n"))
        assertEquals("1.0", ShellParsers.normalizeScale("1"))
        assertNull(ShellParsers.normalizeScale("null"))
        assertEquals("+3h2m ago", ShellParsers.parseAppOpLastAccess("CAMERA: allow; time=+3h2m ago; duration=+5s\n", "CAMERA"))
        assertNull(ShellParsers.parseAppOpLastAccess("CAMERA: allow\n", "CAMERA"))
        assertNull(ShellParsers.parseAppOpLastAccess("No operations.", "CAMERA"))
    }

    @Test fun densityAndAnimationVerification() = runTest {
        val t = DisplayTransport()
        val engine = ExecutionEngine(t)
        val v = Verifiers(t, 0)
        val cmd = ShellCommand.WmDensity(360)
        val r = engine.run(cmd, confirmed = true, verification = v.forCommand(cmd))
        assertTrue(r.isConfirmedSuccess)
        assertEquals("default", r.previousState)
        assertEquals("dpi:360", r.verifiedState)
        val inv = Rollback.inverse(cmd, r.previousState)
        assertEquals(ShellCommand.WmDensityReset, inv)
        val back = engine.run(inv!!, verification = v.forCommand(inv))
        assertTrue(back.isConfirmedSuccess)
        assertNull(t.override)

        val a = ShellCommand.SettingsPutGlobal("window_animation_scale", "0.5")
        val ra = engine.run(a, verification = v.forCommand(a))
        assertTrue(ra.isConfirmedSuccess)
        assertEquals("default", ra.previousState)
    }
}
