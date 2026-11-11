package com.miftah.core.capability

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.miftah.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads who holds powerful access, straight from platform APIs. Anything Android refuses to answer is counted, not guessed. */
class PrivacyScanner(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun scan(): PrivacyReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
        val byPkg = packages.associateBy { it.packageName }
        val findings = LinkedHashSet<PrivacyFinding>()
        var unreadable = 0

        fun add(cat: PrivacyCategory, pkg: String) {
            val p = byPkg[pkg] ?: return
            val ai = p.applicationInfo ?: return
            findings += PrivacyFinding(cat, pkg, pm.getApplicationLabel(ai).toString(), ai.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        }

        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.forEach { svc ->
                svc.resolveInfo?.serviceInfo?.packageName?.let { add(PrivacyCategory.ACCESSIBILITY, it) }
            }
        } catch (e: SecurityException) { unreadable++ }

        try {
            NotificationManagerCompat.getEnabledListenerPackages(context).forEach { add(PrivacyCategory.NOTIFICATION_LISTENER, it) }
        } catch (e: SecurityException) { unreadable++ }

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.activeAdmins?.forEach { add(PrivacyCategory.DEVICE_ADMIN, it.packageName) }
        } catch (e: SecurityException) { unreadable++ }

        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        fun allowed(op: String, p: PackageInfo, ai: ApplicationInfo): Boolean? {
            if (ops == null) return null
            return try {
                val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(op, ai.uid, p.packageName)
                else @Suppress("DEPRECATION") ops.checkOpNoThrow(op, ai.uid, p.packageName)
                mode == AppOpsManager.MODE_ALLOWED
            } catch (e: SecurityException) {
                null
            }
        }

        val grants = LinkedHashMap<String, MutableList<String>>()
        for (p in packages) {
            if (p.packageName == context.packageName) continue
            val ai = p.applicationInfo ?: continue
            val requested = p.requestedPermissions ?: continue
            if ("android.permission.SYSTEM_ALERT_WINDOW" in requested) {
                when (allowed("android:system_alert_window", p, ai)) { true -> add(PrivacyCategory.OVERLAY, p.packageName); null -> unreadable++; false -> {} }
            }
            if ("android.permission.PACKAGE_USAGE_STATS" in requested) {
                when (allowed("android:get_usage_stats", p, ai)) { true -> add(PrivacyCategory.USAGE_ACCESS, p.packageName); null -> unreadable++; false -> {} }
            }
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in requested) {
                when (allowed("android:request_install_packages", p, ai)) { true -> add(PrivacyCategory.INSTALL_UNKNOWN, p.packageName); null -> unreadable++; false -> {} }
            }
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val granted = p.requestedPermissionsFlags
                requested.forEachIndexed { i, name ->
                    if (name in PrivacyScore.SENSITIVE_PERMISSIONS && granted != null && i < granted.size &&
                        (granted[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    ) grants.getOrPut(name) { mutableListOf() } += p.packageName
                }
            }
        }
        PrivacyReport(
            findings = findings.toList(),
            sensitiveGrants = PrivacyScore.SENSITIVE_PERMISSIONS.mapNotNull { perm -> grants[perm]?.let { SensitiveGrant(perm, it.sorted()) } },
            scannedPackages = packages.size,
            unreadable = unreadable,
            readAtMillis = System.currentTimeMillis(),
        )
    }
}
