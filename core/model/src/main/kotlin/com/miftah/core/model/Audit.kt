package com.miftah.core.model

data class AuditEntry(val target: String, val result: OperationResult)

object AuditExport {
    fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun jsonOrNull(s: String?): String = if (s == null) "null" else jsonString(s)

    fun toJson(entries: List<AuditEntry>): String = buildString {
        append("{\"version\":1,\"entries\":[")
        entries.forEachIndexed { i, e ->
            if (i > 0) append(',')
            val r = e.result
            append("{\"id\":").append(jsonString(r.operationId))
            append(",\"name\":").append(jsonString(r.name))
            append(",\"target\":").append(jsonString(e.target))
            append(",\"timestampMillis\":").append(r.timestampMillis)
            append(",\"accessLevel\":").append(jsonString(r.accessLevel.name))
            append(",\"outcome\":").append(jsonString(r.outcome.name))
            append(",\"exitCode\":").append(r.exitCode?.toString() ?: "null")
            append(",\"previousState\":").append(jsonOrNull(r.previousState))
            append(",\"verifiedState\":").append(jsonOrNull(r.verifiedState))
            append(",\"verified\":").append(r.verified)
            append(",\"rollback\":").append(jsonString(r.rollback.name))
            append(",\"stdout\":").append(jsonString(r.stdout))
            append(",\"stderr\":").append(jsonString(r.stderr))
            append('}')
        }
        append("]}")
    }

    /** Quotes when needed and neutralises spreadsheet formula prefixes. */
    fun csvCell(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun toCsv(entries: List<AuditEntry>): String = buildString {
        append("id,name,target,timestamp_millis,access_level,outcome,exit_code,verified,rollback,previous_state,verified_state,stdout,stderr\n")
        for (e in entries) {
            val r = e.result
            append(listOf(
                r.operationId, r.name, e.target, r.timestampMillis.toString(), r.accessLevel.name, r.outcome.name,
                r.exitCode?.toString() ?: "", r.verified.toString(), r.rollback.name,
                r.previousState ?: "", r.verifiedState ?: "", r.stdout, r.stderr,
            ).joinToString(",") { csvCell(it) })
            append('\n')
        }
    }
}
