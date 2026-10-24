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
)

enum class AppFilter { ALL, USER, SYSTEM, DISABLED }

object AppQuery {
    fun apply(apps: List<AppInfo>, query: String, filter: AppFilter): List<AppInfo> {
        val q = query.trim()
        return apps.asSequence()
            .filter {
                when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !it.isSystem
                    AppFilter.SYSTEM -> it.isSystem
                    AppFilter.DISABLED -> !it.enabled
                }
            }
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
