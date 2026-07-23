package com.hassan.zensposed.focus

import android.content.Context

/**
 * Persistent, synchronously-readable source of truth for the active session.
 * Stored in a plain SharedPreferences so BootReceiver can read it instantly on boot.
 */
object FocusState {

    private const val PREFS = "focus_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_END_TIME = "end_time"
    private const val KEY_START_TIME = "start_time"
    private const val KEY_PLANNED_MS = "planned_ms"
    private const val KEY_SPACE = "space_name"
    private const val KEY_THEME = "theme_id"
    private const val KEY_NO_LIMIT = "no_limit"
    private const val KEY_WHITELIST = "whitelist_csv"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Session(
        val active: Boolean,
        val startTime: Long,
        val endTime: Long,
        val plannedMs: Long,
        val spaceName: String,
        val themeId: String,
        val noLimit: Boolean,
        val whitelist: Set<String>
    )

    fun start(
        context: Context,
        startTime: Long,
        endTime: Long,
        plannedMs: Long,
        spaceName: String,
        themeId: String,
        noLimit: Boolean,
        whitelist: Set<String>
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_START_TIME, startTime)
            .putLong(KEY_END_TIME, endTime)
            .putLong(KEY_PLANNED_MS, plannedMs)
            .putString(KEY_SPACE, spaceName)
            .putString(KEY_THEME, themeId)
            .putBoolean(KEY_NO_LIMIT, noLimit)
            .putString(KEY_WHITELIST, whitelist.joinToString(","))
            .commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).commit()
    }

    fun isActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        if (p.getBoolean(KEY_NO_LIMIT, false)) return true
        return p.getLong(KEY_END_TIME, 0L) > System.currentTimeMillis()
    }

    fun current(context: Context): Session? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return null
        val csv = p.getString(KEY_WHITELIST, "") ?: ""
        return Session(
            active = true,
            startTime = p.getLong(KEY_START_TIME, 0L),
            endTime = p.getLong(KEY_END_TIME, 0L),
            plannedMs = p.getLong(KEY_PLANNED_MS, 0L),
            spaceName = p.getString(KEY_SPACE, "Focus") ?: "Focus",
            themeId = p.getString(KEY_THEME, "ocean") ?: "ocean",
            noLimit = p.getBoolean(KEY_NO_LIMIT, false),
            whitelist = csv.split(",").filter { it.isNotBlank() }.toSet()
        )
    }
}
