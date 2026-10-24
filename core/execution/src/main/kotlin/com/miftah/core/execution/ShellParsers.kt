package com.miftah.core.execution

data class PackageListParse(val packages: Set<String>, val malformedLines: Int)
data class ShellIdentity(val uid: Int, val name: String)

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

    fun parseIdentity(stdout: String): ShellIdentity? {
        val m = Regex("uid=(\\d+)\\(([^)]*)\\)").find(stdout) ?: return null
        return ShellIdentity(m.groupValues[1].toInt(), m.groupValues[2])
    }
}

object Redactor {
    private val secret = Regex("(?i)(password|passwd|token|secret|pairing[ _-]?code|private[ _-]?key)(\\s*[=:]\\s*)\\S+")
    fun redact(text: String): String = secret.replace(text) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
}
