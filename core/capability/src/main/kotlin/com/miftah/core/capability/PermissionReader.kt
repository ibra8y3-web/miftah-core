package com.miftah.core.capability

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.miftah.core.model.PermissionEntry
import com.miftah.core.model.Protection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads permissions the package REQUESTS, as PackageManager reports them. Scope limit: AppOps and
 * special app-access toggles are not part of this list.
 */
class PermissionReader(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun read(pkg: String): List<PermissionEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(pkg, flags)
        }
        val names = info.requestedPermissions ?: return@withContext emptyList()
        val granted = info.requestedPermissionsFlags
        names.mapIndexed { i, name ->
            val isGranted = granted != null && i < granted.size && (granted[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            val level = try { pm.getPermissionInfo(name, 0).protectionLevel } catch (e: PackageManager.NameNotFoundException) { null }
            PermissionEntry(name, isGranted, Protection.classify(level))
        }.sortedBy { it.name }
    }

    /** Independent platform read used to verify pm grant/revoke: "granted" or "denied". */
    fun state(pkg: String, permission: String): String =
        if (context.packageManager.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
}
