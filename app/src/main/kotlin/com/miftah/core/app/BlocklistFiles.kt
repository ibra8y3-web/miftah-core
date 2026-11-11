package com.miftah.core.app

import android.content.Context
import com.miftah.core.net.Blocklist
import com.miftah.core.net.BlocklistParser
import java.io.File

data class BlocklistCounts(val builtIn: Int, val imported: Int, val custom: Int, val allowed: Int)

/** Blocklists live in app-private files; nothing is uploaded anywhere. */
object BlocklistFiles {
    const val IMPORTED = "blocklist_imported.txt"
    const val CUSTOM = "blocklist_custom.txt"
    const val ALLOW = "blocklist_allow.txt"

    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun lines(ctx: Context, name: String): List<String> =
        file(ctx, name).takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() }

    private fun builtIn(ctx: Context): Set<String> =
        BlocklistParser.parse(ctx.resources.openRawResource(R.raw.blocklist_default).bufferedReader().use { it.readText() })

    fun load(ctx: Context): Blocklist =
        Blocklist(builtIn(ctx) + lines(ctx, IMPORTED) + lines(ctx, CUSTOM), lines(ctx, ALLOW))

    fun counts(ctx: Context) =
        BlocklistCounts(builtIn(ctx).size, lines(ctx, IMPORTED).size, lines(ctx, CUSTOM).size, lines(ctx, ALLOW).size)

    /** Adds one validated domain; false if the text is not a plain domain name. */
    fun add(ctx: Context, name: String, raw: String): Boolean {
        val d = BlocklistParser.normalizeDomain(raw) ?: return false
        if (d !in lines(ctx, name)) file(ctx, name).appendText(d + "\n")
        return true
    }

    fun clear(ctx: Context, name: String) { file(ctx, name).delete() }

    fun writeImported(ctx: Context, domains: Set<String>) {
        file(ctx, IMPORTED).writeText(domains.joinToString("\n") + "\n")
    }
}
