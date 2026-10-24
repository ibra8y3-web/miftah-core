package com.miftah.core.execution

/**
 * The legacy ADB `shell:` service returns merged output without an exit code. We append a marker line
 * carrying a per-call random nonce so the exit code can be recovered and cannot be spoofed by command output.
 */
object ExitMarker {
    private val NONCE = Regex("^[A-Za-z0-9]+$")

    fun wrap(command: String, nonce: String): String {
        require(NONCE.matches(nonce)) { "Invalid nonce" }
        return "$command; echo \"__MIFTAH_EXIT_${nonce}:\$?\""
    }

    fun parse(output: String, nonce: String): RawShellResult {
        val prefix = "__MIFTAH_EXIT_${nonce}:"
        val lines = output.lines()
        val idx = lines.indexOfLast { it.trim().startsWith(prefix) }
        if (idx < 0) return RawShellResult(null, output, "")
        val code = lines[idx].trim().removePrefix(prefix).toIntOrNull()
        return RawShellResult(code, lines.subList(0, idx).joinToString("\n"), "")
    }
}
