package com.miftah.core.execution

data class PackageListParse(val packages: Set<String>, val malformedLines: Int)
data class ShellIdentity(val uid: Int, val name: String)
data class DensityInfo(val physical: Int?, val override: Int?)

object ShellParsers {
    fun parsePackageList(stdout: String): PackageListParse {
        val pkgs = linkedSetOf<String>()
        var bad = 0
        for (raw in stdout.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val name = line.removePrefix("package:").takeIf { line.startsWith("package:") }?.trim()
            if (name.isNullOrEmpty() || !Regex("^[A-Za-z][A-Za-z0-9_.]*$").matches(name)) bad++ else pkgs += name
        }
        return PackageListParse(pkgs, bad)
    }

    /** Parses `OP_NAME: allow` style lines (optionally prefixed by `Uid mode:`). Returns null if absent. */
    fun parseAppOpMode(stdout: String, op: String): String? {
        val m = Regex("(?m)^\\s*(?:Uid mode:\\s*)?(?:OP_)?" + Regex.escape(op) + ":\\s*([a-z_]+)").find(stdout)
        return m?.groupValues?.get(1)
    }

    /** `wm density` prints "Physical density: N" and, only when changed, "Override density: M". */
    fun parseDensity(stdout: String): DensityInfo? {
        val phys = Regex("Physical density:\\s*(\\d+)").find(stdout)?.groupValues?.get(1)?.toIntOrNull()
        val over = Regex("Override density:\\s*(\\d+)").find(stdout)?.groupValues?.get(1)?.toIntOrNull()
        return if (phys == null && over == null) null else DensityInfo(phys, over)
    }

    fun normalizeScale(s: String): String? = s.trim().toDoubleOrNull()?.toString()

    /** Raw "last access" text from a line such as `CAMERA: allow; time=+3h2m ago`; null when never accessed/unknown. */
    fun parseAppOpLastAccess(stdout: String, op: String): String? {
        val line = stdout.lineSequence().firstOrNull { it.contains("$op:") } ?: return null
        return Regex("time=([^;\\n]+)").find(line)?.groupValues?.get(1)?.trim()
    }

    fun parseIdentity(stdout: String): ShellIdentity? {
        val m = Regex("uid=(\\d+)\\(([^)]*)\\)").find(stdout) ?: return null
        return ShellIdentity(m.groupValues[1].toInt(), m.groupValues[2])
    }
}

object Redactor {
    private val secret = Regex("(?i)(password|passwd|token|secret|pairing[ _-]?code|private[ _-]?key)(\\s*[=:]\\s*)\\S+")
    fun redact(text: String): String = secret.replace(text) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
}
