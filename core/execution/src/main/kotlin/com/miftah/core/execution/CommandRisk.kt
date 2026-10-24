package com.miftah.core.execution

enum class Risk { NORMAL, DESTRUCTIVE }

/** Heuristic used only to ask for extra confirmation in the interactive terminal; it is not a security boundary. */
object CommandRisk {
    private val patterns = listOf(
        Regex("""(^|[\s;&|])rm(\s|$)"""),
        Regex("""\bpm\s+(uninstall|clear|disable|disable-user|hide|suspend|trim-caches|reset-permissions|revoke|grant|install)\b"""),
        Regex("""\bcmd\s+package\s+(uninstall|clear|disable)"""),
        Regex("""\bam\s+(force-stop|kill|kill-all|crash)\b"""),
        Regex("""\b(reboot|shutdown|wipe|dd|mkfs|format|setprop|svc|mv|chmod|chown|kill|killall|pkill)\b"""),
        Regex("""\bsettings\s+put\b"""),
        Regex("""\b(cmd\s+)?appops\s+set\b"""),
        Regex("""\bcontent\s+(delete|update|insert)\b"""),
        Regex(""">>?\s*\S"""),
    )

    fun isAcceptable(command: String): Boolean =
        command.isNotBlank() && command.length <= 2000 && command.none { it.code < 0x20 }

    fun classify(command: String): Risk =
        if (patterns.any { it.containsMatchIn(command) }) Risk.DESTRUCTIVE else Risk.NORMAL
}
