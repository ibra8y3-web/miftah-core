package com.miftah.core.capability

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.miftah.core.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the real package list from PackageManager (including packages removed for this user). */
class PackageInventory(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun load(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
        packages.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            AppInfo(
                packageName = p.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                versionName = p.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong(),
                isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                isUpdatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                enabled = ai.enabled,
                firstInstallMillis = p.firstInstallTime,
                lastUpdateMillis = p.lastUpdateTime,
                installed = ai.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            )
        }
    }
}
