package com.miftah.core.execution

import com.miftah.core.model.AccessLevel

enum class AdbState { NOT_CONFIGURED, AWAITING_PAIRING, PAIRING, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

data class RawShellResult(val exitCode: Int?, val stdout: String, val stderr: String)

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Boundary to whichever ADB implementation is chosen later (Phase C). Nothing outside this
 * interface may depend on a specific ADB library.
 */
interface AdbTransport {
    val accessLevel: AccessLevel
    /** Runs one already-validated shell command string. Throws [TransportException] on connection problems. */
    suspend fun execute(command: String): RawShellResult
}
