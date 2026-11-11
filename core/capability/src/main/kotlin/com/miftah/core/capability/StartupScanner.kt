package com.miftah.core.capability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.miftah.core.model.RawReceiver
import com.miftah.core.model.StartupEntry
import com.miftah.core.model.StartupGrouping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Finds apps that register for the boot broadcasts, and reads each component's real enabled state. */
class StartupScanner(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun scan(): List<StartupEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        val raw = ArrayList<RawReceiver>()
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            val intent = Intent(action)
            val found: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                pm.queryBroadcastReceivers(intent, flags)
            }
            for (ri in found) {
                val info = ri.activityInfo ?: continue
                if (info.packageName == context.packageName) continue
                val ai = info.applicationInfo ?: continue
                raw += RawReceiver(
                    pkg = info.packageName,
                    label = pm.getApplicationLabel(ai).toString(),
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    className = info.name,
                    enabled = state(info.packageName, info.name) != "disabled",
                )
            }
        }
        StartupGrouping.group(raw)
    }

    /** Independent platform read used to verify pm enable/disable: "enabled", "disabled" or null if unreadable. */
    @Suppress("DEPRECATION")
    fun state(pkg: String, className: String): String? = try {
        val pm = context.packageManager
        val cn = ComponentName(pkg, className)
        when (pm.getComponentEnabledSetting(cn)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "enabled"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "disabled"
            else -> if (pm.getReceiverInfo(cn, PackageManager.MATCH_DISABLED_COMPONENTS).isEnabled) "enabled" else "disabled"
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
