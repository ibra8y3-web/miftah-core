package com.miftah.core.model

data class ComponentEntry(val className: String, val enabled: Boolean)

data class StartupEntry(val pkg: String, val label: String, val isSystem: Boolean, val components: List<ComponentEntry>)

data class RawReceiver(val pkg: String, val label: String, val isSystem: Boolean, val className: String, val enabled: Boolean)

object StartupGrouping {
    /** Groups boot receivers per app; user apps first, then by name. Duplicates are dropped. */
    fun group(raw: List<RawReceiver>): List<StartupEntry> =
        raw.distinctBy { it.pkg to it.className }
            .groupBy { it.pkg }
            .map { (pkg, items) ->
                StartupEntry(
                    pkg, items.first().label, items.first().isSystem,
                    items.map { ComponentEntry(it.className, it.enabled) }.sortedBy { it.className },
                )
            }
            .sortedWith(compareBy<StartupEntry>({ it.isSystem }, { it.label.lowercase() }))
}
