package com.miftah.core.net

/** Domain blocklist with subdomain matching and an allow-list that always wins. */
class Blocklist(blocked: Collection<String>, allowed: Collection<String> = emptyList()) {
    private val blockedSet: Set<String> = blocked.map(::normalize).filter { it.isNotEmpty() }.toHashSet()
    private val allowedSet: Set<String> = allowed.map(::normalize).filter { it.isNotEmpty() }.toHashSet()
    val size: Int get() = blockedSet.size

    fun isBlocked(name: String): Boolean {
        val n = normalize(name)
        if (n.isEmpty()) return false
        if (matches(allowedSet, n)) return false
        return matches(blockedSet, n)
    }

    private fun matches(set: Set<String>, name: String): Boolean {
        var cur = name
        while (true) {
            if (cur in set) return true
            val dot = cur.indexOf('.')
            if (dot < 0) return false
            cur = cur.substring(dot + 1)
            if (!cur.contains('.')) return false // never match a bare TLD
        }
    }

    companion object {
        fun normalize(s: String): String = s.trim().lowercase().removeSuffix(".")
    }
}

object BlocklistParser {
    private val DOMAIN = Regex("^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$")
    private val IPV4 = Regex("^\\d+(\\.\\d+){3}$")
    private val HOSTS_IPS = setOf("0.0.0.0", "127.0.0.1", "::1", "::", "0")

    /** Returns a clean domain or null if the text is not a plain domain name. */
    fun normalizeDomain(raw: String): String? {
        val d = Blocklist.normalize(raw)
        if (d.length > 253 || !d.contains('.') || !DOMAIN.matches(d) || IPV4.matches(d) || d.contains("..")) return null
        return d
    }

    /** Understands hosts files, plain domain lists and the simple `||domain^` AdBlock form. */
    fun parse(text: String, maxEntries: Int = 500_000): Set<String> {
        val out = LinkedHashSet<String>()
        for (rawLine in text.lineSequence()) {
            if (out.size >= maxEntries) break
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
            val hash = line.indexOf('#')
            if (hash > 0) line = line.substring(0, hash).trim()
            if (line.startsWith("||")) {
                val end = line.indexOf('^')
                if (end > 2) normalizeDomain(line.substring(2, end))?.let { out += it }
                continue
            }
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size >= 2 && tokens[0] in HOSTS_IPS) {
                tokens.drop(1).forEach { t -> normalizeDomain(t)?.let { out += it } }
            } else if (tokens.size == 1) {
                normalizeDomain(tokens[0])?.let { out += it }
            }
        }
        return out
    }
}
