package com.hassan.zensposed.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hassan.zensposed.core.AppResolver
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.data.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zen_sposed_settings")

data class FocusSettings(
    val onboardingDone: Boolean = false,
    val whitelist: Set<String> = emptySet(),
    val allowNotificationPanel: Boolean = false,
    /** When true, StatusBarManager.DISABLE_HOME is applied during an active session. */
    val blockHomeButton: Boolean = false,
    /** When true, StatusBarManager.DISABLE_RECENT is applied during an active session. */
    val blockRecentApps: Boolean = false,
    val allowQuickToggles: Boolean = false,
    val themeId: String = "ocean",
    val aodEnabled: Boolean = false,
    val hasPassword: Boolean = false,
    /** "password" or "qr" — only one emergency-exit method is active. */
    val exitMethod: String = EXIT_PASSWORD,
    /**
     * App chrome appearance (settings / home), not the focus-screen nature theme.
     * One of [APPEARANCE_SYSTEM], [APPEARANCE_LIGHT], [APPEARANCE_DARK].
     */
    val appearanceMode: String = APPEARANCE_SYSTEM,
    /** Last Deep Zen wheel selection, in total minutes. */
    val deepZenMinutes: Int = 120,
    /** Five quick-start durations (minutes) shown on the home Deep Zen card. */
    val quickTimersMinutes: List<Int> = DEFAULT_QUICK_TIMERS
) {
    companion object {
        const val EXIT_PASSWORD = "password"
        const val EXIT_QR = "qr"
        const val APPEARANCE_SYSTEM = "system"
        const val APPEARANCE_LIGHT = "light"
        const val APPEARANCE_DARK = "dark"
        val DEFAULT_QUICK_TIMERS: List<Int> = listOf(120, 240, 480, 720, 1440) // 2h 4h 8h 12h 24h
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val WHITELIST = stringSetPreferencesKey("whitelist")
        val ALLOW_PANEL = booleanPreferencesKey("allow_panel")
        val BLOCK_HOME = booleanPreferencesKey("block_home")
        val BLOCK_RECENT = booleanPreferencesKey("block_recent")
        val ALLOW_TOGGLES = booleanPreferencesKey("allow_toggles")
        val THEME = stringPreferencesKey("theme_id")
        val AOD = booleanPreferencesKey("aod_enabled")
        val HAS_PASSWORD = booleanPreferencesKey("has_password")
        val EXIT_METHOD = stringPreferencesKey("exit_method")
        val APPEARANCE = stringPreferencesKey("appearance_mode")
        val PROFILES = stringPreferencesKey("profiles_json")
        val DEEP_ZEN_MINUTES = intPreferencesKey("deep_zen_minutes")
        val QUICK_TIMERS = stringPreferencesKey("quick_timers_csv")
    }

    val profiles: Flow<List<Profile>> = context.dataStore.data.map { p ->
        val json = p[Keys.PROFILES]
        if (json.isNullOrBlank()) defaultProfiles() else parseProfiles(json)
    }

    suspend fun saveProfiles(list: List<Profile>) =
        context.dataStore.edit { it[Keys.PROFILES] = serializeProfiles(list) }

    suspend fun upsertProfile(profile: Profile) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES]?.let { parseProfiles(it) } ?: defaultProfiles()
            val updated = current.toMutableList()
            val idx = updated.indexOfFirst { it.id == profile.id }
            if (idx >= 0) updated[idx] = profile else updated.add(profile)
            prefs[Keys.PROFILES] = serializeProfiles(updated)
        }
    }

    suspend fun deleteProfile(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES]?.let { parseProfiles(it) } ?: defaultProfiles()
            prefs[Keys.PROFILES] = serializeProfiles(current.filterNot { it.id == id })
        }
    }

    private fun defaultProfiles(): List<Profile> = listOf(
        Profile("default_work", "Work", 45),
        Profile("default_life", "Life", 0, noLimit = true),
        Profile("default_learning", "Learning", 60)
    )

    private fun serializeProfiles(list: List<Profile>): String {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("durationMinutes", p.durationMinutes)
            obj.put("noLimit", p.noLimit)
            obj.put("whitelist", JSONArray(p.whitelist.toList()))
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseProfiles(json: String): List<Profile> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val wl = obj.optJSONArray("whitelist")
            val set = if (wl != null) (0 until wl.length()).map { wl.getString(it) }.toSet() else emptySet()
            Profile(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Profile"),
                durationMinutes = obj.optInt("durationMinutes", 25),
                noLimit = obj.optBoolean("noLimit", false),
                whitelist = set
            )
        }
    }.getOrElse { defaultProfiles() }

    val settings: Flow<FocusSettings> = context.dataStore.data.map { p ->
        val raw = p[Keys.WHITELIST] ?: emptySet()
        // Never count the always-allowed defaults as "extra" whitelist entries.
        val defaults = AppResolver.defaultUiPackages(context) +
            AppResolver.alwaysAllowedPackages(context)
        val onboarding = p[Keys.ONBOARDING] ?: false
        // Keep the sync mirror warm for future cold starts (covers users who
        // completed onboarding before this mirror existed).
        if (onboarding) writeOnboardingSync(context, true)
        val deepZen = (p[Keys.DEEP_ZEN_MINUTES]
            ?: deepZenMinutesSync(context)
            ?: 120).coerceIn(1, Constants.MAX_TIMER_MINUTES)
        // Keep sync mirror warm so cold start shows the last timer, not the 2h default.
        writeDeepZenMinutesSync(context, deepZen)
        FocusSettings(
            onboardingDone = onboarding,
            whitelist = raw - defaults,
            allowNotificationPanel = p[Keys.ALLOW_PANEL] ?: false,
            blockHomeButton = p[Keys.BLOCK_HOME] ?: false,
            blockRecentApps = p[Keys.BLOCK_RECENT] ?: false,
            allowQuickToggles = p[Keys.ALLOW_TOGGLES] ?: false,
            themeId = p[Keys.THEME] ?: "ocean",
            aodEnabled = p[Keys.AOD] ?: false,
            hasPassword = p[Keys.HAS_PASSWORD] ?: false,
            exitMethod = p[Keys.EXIT_METHOD] ?: FocusSettings.EXIT_PASSWORD,
            appearanceMode = normalizeAppearance(p[Keys.APPEARANCE]),
            deepZenMinutes = deepZen,
            quickTimersMinutes = parseQuickTimers(p[Keys.QUICK_TIMERS])
        )
    }

    private fun normalizeAppearance(raw: String?): String = when (raw) {
        FocusSettings.APPEARANCE_LIGHT, FocusSettings.APPEARANCE_DARK -> raw
        else -> FocusSettings.APPEARANCE_SYSTEM
    }

    private fun parseQuickTimers(csv: String?): List<Int> {
        val defaults = FocusSettings.DEFAULT_QUICK_TIMERS
        if (csv.isNullOrBlank()) return defaults
        val parsed = csv.split(",")
            .mapNotNull { it.trim().toIntOrNull()?.coerceIn(1, Constants.MAX_TIMER_MINUTES) }
        return if (parsed.size == 5) parsed else defaults
    }

    suspend fun setOnboardingDone(done: Boolean) {
        writeOnboardingSync(context, done)
        context.dataStore.edit { it[Keys.ONBOARDING] = done }
    }

    companion object {
        private const val SYNC_PREFS = "zen_sposed_sync"
        private const val SYNC_ONBOARDING = "onboarding_done"
        private const val SYNC_DEEP_ZEN = "deep_zen_minutes"

        fun isOnboardingDoneSync(context: Context): Boolean =
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .getBoolean(SYNC_ONBOARDING, false)

        fun writeOnboardingSync(context: Context, done: Boolean) {
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(SYNC_ONBOARDING, done).apply()
        }

        fun deepZenMinutesSync(context: Context): Int? {
            val prefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains(SYNC_DEEP_ZEN)) return null
            return prefs.getInt(SYNC_DEEP_ZEN, 120).coerceIn(1, Constants.MAX_TIMER_MINUTES)
        }

        fun writeDeepZenMinutesSync(context: Context, minutes: Int) {
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(SYNC_DEEP_ZEN, minutes.coerceIn(1, Constants.MAX_TIMER_MINUTES))
                .apply()
        }
    }

    suspend fun setWhitelist(packages: Set<String>) =
        context.dataStore.edit {
            val defaults = AppResolver.defaultUiPackages(context) +
                AppResolver.alwaysAllowedPackages(context)
            it[Keys.WHITELIST] = packages - defaults
        }

    suspend fun setAllowNotificationPanel(allow: Boolean) =
        context.dataStore.edit { it[Keys.ALLOW_PANEL] = allow }

    suspend fun setBlockHomeButton(block: Boolean) =
        context.dataStore.edit { it[Keys.BLOCK_HOME] = block }

    suspend fun setBlockRecentApps(block: Boolean) =
        context.dataStore.edit { it[Keys.BLOCK_RECENT] = block }

    suspend fun setAllowQuickToggles(allow: Boolean) =
        context.dataStore.edit { it[Keys.ALLOW_TOGGLES] = allow }

    suspend fun setTheme(themeId: String) = context.dataStore.edit { it[Keys.THEME] = themeId }

    suspend fun setAppearanceMode(mode: String) = context.dataStore.edit {
        it[Keys.APPEARANCE] = normalizeAppearance(mode)
    }

    suspend fun setAod(enabled: Boolean) = context.dataStore.edit { it[Keys.AOD] = enabled }

    suspend fun setHasPassword(has: Boolean) = context.dataStore.edit { it[Keys.HAS_PASSWORD] = has }

    suspend fun setExitMethod(method: String) = context.dataStore.edit {
        it[Keys.EXIT_METHOD] = if (method == FocusSettings.EXIT_QR) FocusSettings.EXIT_QR
        else FocusSettings.EXIT_PASSWORD
    }

    suspend fun setDeepZenMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, Constants.MAX_TIMER_MINUTES)
        writeDeepZenMinutesSync(context, clamped)
        context.dataStore.edit { it[Keys.DEEP_ZEN_MINUTES] = clamped }
    }

    suspend fun setQuickTimers(minutes: List<Int>) = context.dataStore.edit {
        val normalized = (minutes.take(5) + FocusSettings.DEFAULT_QUICK_TIMERS)
            .take(5)
            .map { m -> m.coerceIn(1, Constants.MAX_TIMER_MINUTES) }
        it[Keys.QUICK_TIMERS] = normalized.joinToString(",")
    }
}
