package com.miftah.core.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val enabled: Boolean,
    val firstInstallMillis: Long,
    val lastUpdateMillis: Long,
    /** False when the package exists on the device but is removed for the current user. */
    val installed: Boolean = true,
)

enum class AppFilter { ALL, USER, SYSTEM, DISABLED, REMOVED }

object AppQuery {
    fun apply(apps: List<AppInfo>, query: String, filter: AppFilter): List<AppInfo> {
        val q = query.trim()
        return apps.asSequence()
            .filter {
                when (filter) {
                    AppFilter.ALL -> it.installed
                    AppFilter.USER -> it.installed && !it.isSystem
                    AppFilter.SYSTEM -> it.installed && it.isSystem
                    AppFilter.DISABLED -> it.installed && !it.enabled
                    AppFilter.REMOVED -> !it.installed
                }
            }
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
