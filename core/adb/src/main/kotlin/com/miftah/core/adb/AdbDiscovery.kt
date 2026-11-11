package com.miftah.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Finds the Wireless-debugging pairing and connect ports through the platform's mDNS (NsdManager),
 * so the user never has to type a port. A port is exposed only while its service is actually advertised.
 */
@Suppress("DEPRECATION")
class AdbDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())

    private val _pairingPort = MutableStateFlow<Int?>(null)
    val pairingPort: StateFlow<Int?> = _pairingPort
    private val _connectPort = MutableStateFlow<Int?>(null)
    val connectPort: StateFlow<Int?> = _connectPort
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null
    private var pairingRefs = 0
    private var connectRefs = 0

    @Synchronized fun startPairing() {
        pairingRefs++
        if (pairingListener == null) pairingListener = begin(PAIRING_TYPE, _pairingPort)
    }

    @Synchronized fun stopPairing() {
        if (pairingRefs > 0) pairingRefs--
        if (pairingRefs == 0) { pairingListener = end(pairingListener); _pairingPort.value = null }
    }

    @Synchronized fun startConnect() {
        connectRefs++
        if (connectListener == null) connectListener = begin(CONNECT_TYPE, _connectPort)
    }

    @Synchronized fun stopConnect() {
        if (connectRefs > 0) connectRefs--
        if (connectRefs == 0) { connectListener = end(connectListener); _connectPort.value = null }
    }

    private fun begin(type: String, sink: MutableStateFlow<Int?>): NsdManager.DiscoveryListener? {
        val listener = object : NsdManager.DiscoveryListener {
            @Volatile private var resolvedName: String? = null
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _error.value = "mDNS discovery failed to start (code $errorCode)"
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo, sink, 0) { resolvedName = it }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName == resolvedName) { sink.value = null; resolvedName = null }
            }
        }
        return try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            _error.value = null
            listener
        } catch (e: IllegalArgumentException) {
            _error.value = "mDNS discovery unavailable: ${e.message}"
            null
        }
    }

    private fun end(listener: NsdManager.DiscoveryListener?): NsdManager.DiscoveryListener? {
        if (listener != null) {
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                // Listener was not registered any more; nothing to stop.
            }
        }
        return null
    }

    private fun resolve(info: NsdServiceInfo, sink: MutableStateFlow<Int?>, attempt: Int, onName: (String) -> Unit) {
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (attempt < 4) main.postDelayed({ resolve(serviceInfo, sink, attempt + 1, onName) }, 400L)
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    onName(serviceInfo.serviceName)
                    sink.value = serviceInfo.port
                }
            })
        } catch (e: IllegalArgumentException) {
            if (attempt < 4) main.postDelayed({ resolve(info, sink, attempt + 1, onName) }, 400L)
        }
    }

    private companion object {
        const val PAIRING_TYPE = "_adb-tls-pairing._tcp"
        const val CONNECT_TYPE = "_adb-tls-connect._tcp"
    }
}
