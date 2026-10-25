package com.miftah.core.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.miftah.core.adb.AdbDiscovery
import com.miftah.core.adb.LibAdbTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Guided pairing. The port is found by mDNS; Android never exposes the 6-digit code to apps, so the user
 * types only the code, either in the app or in a notification reply (so the settings screen can stay open).
 */
class PairingCoordinator(
    private val app: Application,
    private val transport: LibAdbTransport,
    private val discovery: AdbDiscovery,
    private val scope: CoroutineScope,
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active
    private var timeoutJob: Job? = null

    fun begin() {
        if (_active.value) return
        discovery.startPairing()
        discovery.startConnect()
        _active.value = true
        post(app.getString(R.string.notif_waiting), showInput = true)
        timeoutJob = scope.launch {
            delay(AUTO_STOP_MS)
            cancel()
        }
    }

    fun cancel() {
        if (!_active.value) return
        timeoutJob?.cancel()
        discovery.stopPairing()
        discovery.stopConnect()
        _active.value = false
        NotificationManagerCompat.from(app).cancel(NOTIF_ID)
    }

    /** Pairs with the detected port, then connects if the connect service is advertised. */
    suspend fun submitCode(code: String, notify: Boolean = true): Boolean {
        val say = { text: String, input: Boolean -> if (notify) post(text, input) }
        val port = discovery.pairingPort.value
        if (port == null) {
            say(app.getString(R.string.notif_no_dialog), true)
            return false
        }
        say(app.getString(R.string.notif_pairing), false)
        if (!transport.pair(LOOPBACK, port, code)) {
            say(app.getString(R.string.notif_failed, transport.lastError.value ?: "?"), true)
            return false
        }
        say(app.getString(R.string.notif_connecting), false)
        val connectPort = withTimeoutOrNull(CONNECT_WAIT_MS) { discovery.connectPort.filterNotNull().first() }
        if (connectPort == null) {
            say(app.getString(R.string.notif_paired_only), false)
            finish(keepNotification = notify)
            return true
        }
        val ok = transport.connect(LOOPBACK, connectPort)
        say(if (ok) app.getString(R.string.notif_connected) else app.getString(R.string.notif_failed, transport.lastError.value ?: "?"), false)
        finish(keepNotification = notify)
        return ok
    }

    private fun finish(keepNotification: Boolean) {
        timeoutJob?.cancel()
        discovery.stopPairing()
        discovery.stopConnect()
        _active.value = false
        if (!keepNotification) NotificationManagerCompat.from(app).cancel(NOTIF_ID)
    }

    private fun canNotify(): Boolean =
        NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    // Permission is verified by canNotify() right before notify().
    @SuppressLint("MissingPermission")
    private fun post(text: String, showInput: Boolean) {
        if (!canNotify()) return
        val nm = NotificationManagerCompat.from(app)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(app.getString(R.string.notif_channel)).build(),
        )
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(showInput)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (showInput) {
            val intent = Intent(app, PairingReceiver::class.java).setAction(ACTION_CODE)
            val pi = PendingIntent.getBroadcast(app, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val input = RemoteInput.Builder(KEY_CODE).setLabel(app.getString(R.string.notif_enter_code)).build()
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification, app.getString(R.string.notif_enter_code), pi).addRemoteInput(input).build())
        }
        nm.notify(NOTIF_ID, b.build())
    }

    companion object {
        const val KEY_CODE = "pairing_code"
        const val ACTION_CODE = "com.miftah.core.app.ACTION_PAIRING_CODE"
        private const val CHANNEL = "pairing"
        private const val NOTIF_ID = 1001
        private const val LOOPBACK = "127.0.0.1"
        private const val AUTO_STOP_MS = 180_000L
        private const val CONNECT_WAIT_MS = 5_000L
    }
}
