package com.miftah.core.adb

import android.content.Context
import com.miftah.core.execution.*
import com.miftah.core.model.AccessLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The only class that touches libadb-android. Talks to the device's own adbd over loopback.
 * "Connected" is reported only after a real `id` query succeeds and parses.
 */
class LibAdbTransport(context: Context) : AdbTransport {
    private val vault = KeyVault(context.applicationContext)
    private var manager: MiftahConnectionManager? = null
    private val mutex = Mutex()

    private val _state = MutableStateFlow(if (vault.hasKeys()) AdbState.DISCONNECTED else AdbState.NOT_CONFIGURED)
    val state: StateFlow<AdbState> = _state
    private val _error = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _error
    private val _identity = MutableStateFlow<ShellIdentity?>(null)
    val identity: StateFlow<ShellIdentity?> = _identity

    override val accessLevel: AccessLevel
        get() = if (_state.value == AdbState.CONNECTED) AccessLevel.ADB else AccessLevel.STANDARD

    private fun mgr(): MiftahConnectionManager = manager ?: MiftahConnectionManager(vault).also { manager = it }

    private fun validate(host: String, port: Int): String? = when {
        host != "127.0.0.1" && host != "localhost" -> "Only the local device (loopback) is allowed"
        port !in 1..65535 -> "Invalid port"
        else -> null
    }

    private fun fail(message: String): Boolean {
        _error.value = Redactor.redact(message)
        _state.value = AdbState.FAILED
        return false
    }

    suspend fun pair(host: String, port: Int, pairingCode: String): Boolean = mutex.withLock {
        validate(host, port)?.let { return fail(it) }
        if (!Regex("^\\d{6}$").matches(pairingCode)) return fail("The pairing code must be 6 digits")
        _error.value = null
        _state.value = AdbState.PAIRING
        try {
            val ok = withContext(Dispatchers.IO) { mgr().pair(host, port, pairingCode) }
            if (!ok) return fail("Pairing was rejected by the device")
            _state.value = AdbState.DISCONNECTED
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("Pairing failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun connect(host: String, port: Int): Boolean = mutex.withLock {
        validate(host, port)?.let { return fail(it) }
        _error.value = null
        _identity.value = null
        _state.value = AdbState.CONNECTING
        try {
            val ok = withContext(Dispatchers.IO) { mgr().connect(host, port) }
            if (!ok) return fail("The device refused the connection (is it paired? is the port current?)")
            val probe = executeInternal(ShellCommand.Identity.render())
            val id = if (probe.exitCode == 0) ShellParsers.parseIdentity(probe.stdout) else null
            if (id == null) {
                runCatching { withContext(Dispatchers.IO) { mgr().disconnect() } }
                return fail("Connected, but the test query did not return a valid shell identity")
            }
            _identity.value = id
            _state.value = AdbState.CONNECTED
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("Connection failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun disconnect() = mutex.withLock {
        try {
            withContext(Dispatchers.IO) { manager?.disconnect() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = Redactor.redact("Disconnect reported an error: ${e.message ?: e.javaClass.simpleName}")
        }
        _identity.value = null
        _state.value = if (vault.hasKeys()) AdbState.DISCONNECTED else AdbState.NOT_CONFIGURED
    }

    /** Disconnects and deletes the stored identity; the device must be paired again afterwards. */
    suspend fun clearPairing() = mutex.withLock {
        runCatching { withContext(Dispatchers.IO) { manager?.disconnect() } }
        manager = null
        withContext(Dispatchers.IO) { vault.clear() }
        _identity.value = null
        _error.value = null
        _state.value = AdbState.NOT_CONFIGURED
    }

    override suspend fun execute(command: String): RawShellResult {
        if (_state.value != AdbState.CONNECTED) throw TransportException("ADB is not connected")
        try {
            return executeInternal(command)
        } catch (e: TransportException) {
            if (manager?.isConnected != true) _state.value = AdbState.DISCONNECTED
            throw e
        }
    }

    private suspend fun executeInternal(command: String): RawShellResult {
        val m = manager ?: throw TransportException("ADB is not connected")
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val wrapped = ExitMarker.wrap(command, nonce)
        val text = try {
            withContext(Dispatchers.IO) {
                val stream = m.openStream("shell:$wrapped")
                // If the caller cancels (timeout), closing the stream unblocks the pending read.
                val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { stream.close() }
                }
                try {
                    stream.openInputStream().bufferedReader().readText()
                } finally {
                    handle?.dispose()
                    runCatching { stream.close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw TransportException(e.message ?: e.javaClass.simpleName, e)
        }
        return ExitMarker.parse(text, nonce)
    }
}
