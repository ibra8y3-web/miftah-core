package com.miftah.core.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AuditDetail { FULL, MINIMAL }
enum class Upstream { SYSTEM, CLOUDFLARE, GOOGLE, QUAD9 }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val auditDetail: AuditDetail = AuditDetail.FULL,
    val saveTerminal: Boolean = false,
    /** Connect automatically when Wireless debugging is detected and this phone is already paired. */
    val autoConnect: Boolean = true,
    /** Packages excluded from bulk plans. */
    val allowlist: Set<String> = emptySet(),
    val shieldUpstream: Upstream = Upstream.SYSTEM,
    /** Apps that bypass the ad blocker. */
    val shieldExcluded: Set<String> = emptySet(),
    /** Apps cut off from the internet by the firewall. */
    val firewallBlocked: Set<String> = emptySet(),
)

private val Context.settingsStore by preferencesDataStore(name = "miftah_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val themeKey = stringPreferencesKey("theme")
    private val detailKey = stringPreferencesKey("audit_detail")
    private val saveKey = booleanPreferencesKey("save_terminal")
    private val allowKey = stringSetPreferencesKey("allowlist")
    private val autoKey = booleanPreferencesKey("auto_connect")
    private val upstreamKey = stringPreferencesKey("shield_upstream")
    private val excludedKey = stringSetPreferencesKey("shield_excluded")
    private val blockedKey = stringSetPreferencesKey("firewall_blocked")

    val flow: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[themeKey]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            auditDetail = p[detailKey]?.let { v -> AuditDetail.entries.firstOrNull { it.name == v } } ?: AuditDetail.FULL,
            saveTerminal = p[saveKey] ?: false,
            autoConnect = p[autoKey] ?: true,
            allowlist = p[allowKey] ?: emptySet(),
            shieldUpstream = p[upstreamKey]?.let { v -> Upstream.entries.firstOrNull { it.name == v } } ?: Upstream.SYSTEM,
            shieldExcluded = p[excludedKey] ?: emptySet(),
            firewallBlocked = p[blockedKey] ?: emptySet(),
        )
    }

    suspend fun setTheme(t: ThemeMode) { store.edit { it[themeKey] = t.name } }
    suspend fun setAuditDetail(d: AuditDetail) { store.edit { it[detailKey] = d.name } }
    suspend fun setSaveTerminal(v: Boolean) { store.edit { it[saveKey] = v } }
    suspend fun setAutoConnect(v: Boolean) { store.edit { it[autoKey] = v } }
    suspend fun toggleAllow(pkg: String) {
        store.edit {
            val cur = it[allowKey] ?: emptySet()
            it[allowKey] = if (pkg in cur) cur - pkg else cur + pkg
        }
    }

    suspend fun setUpstream(u: Upstream) { store.edit { it[upstreamKey] = u.name } }
    suspend fun addExcluded(pkgs: Set<String>) { store.edit { it[excludedKey] = (it[excludedKey] ?: emptySet()) + pkgs } }
    suspend fun toggleExcluded(pkg: String) {
        store.edit { val c = it[excludedKey] ?: emptySet(); it[excludedKey] = if (pkg in c) c - pkg else c + pkg }
    }
    suspend fun addBlocked(pkgs: Set<String>) { store.edit { it[blockedKey] = (it[blockedKey] ?: emptySet()) + pkgs } }
    suspend fun toggleBlocked(pkg: String) {
        store.edit { val c = it[blockedKey] ?: emptySet(); it[blockedKey] = if (pkg in c) c - pkg else c + pkg }
    }
}
