package com.miftah.core.capability

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.miftah.core.model.BatterySnapshot
import com.miftah.core.model.NetworkSnapshot
import com.miftah.core.model.StorageSnapshot

/** Every value comes from a platform API; anything unreadable is returned as null, never guessed. */
class DiagnosticsReader(private val context: Context) {

    fun storage(): StorageSnapshot? = try {
        val st = StatFs(Environment.getDataDirectory().path)
        StorageSnapshot(st.totalBytes, st.availableBytes, "internal data partition as visible to this app")
    } catch (e: IllegalArgumentException) {
        null
    }

    fun battery(): BatterySnapshot? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
            else -> null
        }
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1).let { if (it < 0) null else it != 0 }
        return BatterySnapshot(
            percent = BatterySnapshot.percentOf(level, scale),
            temperatureC = if (temp == Int.MIN_VALUE) null else temp / 10f,
            status = status,
            plugged = plugged,
        )
    }

    fun network(): NetworkSnapshot? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val net = cm.activeNetwork ?: return NetworkSnapshot(false, emptyList(), null)
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkSnapshot(true, emptyList(), null)
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
        }
        return NetworkSnapshot(true, transports, cm.isActiveNetworkMetered)
    }
}
