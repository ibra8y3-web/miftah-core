package com.miftah.core.capability

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.miftah.core.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the real package list from PackageManager. Never fabricates entries. */
class PackageInventory(private val context: Context) {

    suspend fun load(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledPackages(0)
        }
        packages.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            AppInfo(
                packageName = p.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                versionName = p.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong(),
                isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                isUpdatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                enabled = ai.enabled,
                firstInstallMillis = p.firstInstallTime,
                lastUpdateMillis = p.lastUpdateTime,
            )
        }
    }
}
