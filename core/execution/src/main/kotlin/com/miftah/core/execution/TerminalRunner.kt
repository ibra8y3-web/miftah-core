package com.miftah.core.execution

import kotlinx.coroutines.withTimeoutOrNull

data class TerminalEntry(
    val command: String,
    val startedAtMillis: Long,
    val durationMs: Long,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val error: String?,
)

class TerminalRunner(
    private val transport: AdbTransport,
    private val clock: Clock = Clock { System.currentTimeMillis() },
) {
    suspend fun run(command: String, timeoutMs: Long = 30_000): TerminalEntry {
        val start = clock.nowMillis()
        if (!CommandRisk.isAcceptable(command)) {
            return TerminalEntry(command, start, 0, null, "", "", false, "invalid command")
        }
        val raw = try {
            withTimeoutOrNull(timeoutMs) { transport.execute(command) }
        } catch (e: TransportException) {
            return TerminalEntry(command, start, clock.nowMillis() - start, null, "", "", false, Redactor.redact(e.message ?: "transport error"))
        }
        val duration = clock.nowMillis() - start
        if (raw == null) return TerminalEntry(command, start, duration, null, "", "", true, null)
        return TerminalEntry(command, start, duration, raw.exitCode, Redactor.redact(raw.stdout), Redactor.redact(raw.stderr), false, null)
    }
}
