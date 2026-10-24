package com.miftah.core.execution

import com.miftah.core.model.*
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

fun interface Clock { fun nowMillis(): Long }

/** Independent check of the target state: read before, read after, then decide with [expected]. */
class Verification(
    val probe: suspend () -> String?,
    val expected: (before: String?, after: String?) -> Boolean,
)

class ExecutionEngine(
    private val transport: AdbTransport,
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun run(
        command: ShellCommand,
        timeoutMs: Long = 15_000,
        confirmed: Boolean = false,
        verification: Verification? = null,
    ): OperationResult {
        val id = newId()
        val time = clock.nowMillis()
        val level = transport.accessLevel

        fun result(outcome: Outcome, exit: Int?, out: String, err: String, prev: String?, after: String?, verified: Boolean) =
            OperationResult(id, command.name, time, level, outcome, exit, Redactor.redact(out), Redactor.redact(err),
                prev, after, verified, command.rollback, command.rollbackNote)

        if (command.destructive && !confirmed) {
            return result(Outcome.REJECTED, null, "", "explicit confirmation required for a destructive command", null, null, false)
        }
        val before = readProbe(verification, timeoutMs)
        val raw = try {
            withTimeoutOrNull(timeoutMs) { transport.execute(command.render()) }
        } catch (e: TransportException) {
            return result(Outcome.FAILED, null, "", e.message ?: "transport error", before, null, false)
        }
        if (raw == null) return result(Outcome.TIMED_OUT, null, "", "timed out after ${timeoutMs}ms", before, null, false)
        if (raw.exitCode != 0) return result(Outcome.FAILED, raw.exitCode, raw.stdout, raw.stderr, before, null, false)

        var after: String? = null
        var verified = false
        if (verification != null) {
            after = readProbe(verification, timeoutMs)
            verified = after != null && verification.expected(before, after)
        }
        return result(Outcome.SUCCEEDED, 0, raw.stdout, raw.stderr, before, after, verified)
    }

    private suspend fun readProbe(v: Verification?, timeoutMs: Long): String? =
        try { if (v == null) null else withTimeoutOrNull(timeoutMs) { v.probe() } } catch (e: TransportException) { null }
}
