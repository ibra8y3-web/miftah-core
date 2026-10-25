package com.miftah.core.app

import android.app.Application
import com.miftah.core.adb.AdbDiscovery
import com.miftah.core.adb.LibAdbTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide holders so the notification reply, the UI and the ViewModel share one ADB session. */
class MiftahApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val transport by lazy { LibAdbTransport(this) }
    val discovery by lazy { AdbDiscovery(this) }
    val pairing by lazy { PairingCoordinator(this, transport, discovery, scope) }
}
