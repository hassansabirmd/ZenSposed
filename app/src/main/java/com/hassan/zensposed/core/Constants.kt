package com.hassan.zensposed.core

object Constants {
    const val PACKAGE_NAME = "com.hassan.zensposed"

    const val MAX_TIMER_MINUTES = 24 * 60
    const val MIN_PASSWORD_LENGTH = 20

    // World-readable bridge file that the LSPosed module (running inside
    // system_server / SystemUI) reads to learn the live session state.
    // Written by RootManager via su so the file is readable by system processes.
    const val BRIDGE_DIR = "/data/local/tmp/zensposed"
    const val BRIDGE_FILE = "$BRIDGE_DIR/state.prop"
    /** Written by the system_server LSPosed hook to prove the module is loaded. */
    const val XPOSED_ALIVE_FILE = "/data/system/zensposed_xposed_alive"

    // Bridge keys
    const val KEY_LOCKED = "locked"
    const val KEY_END_TIME = "endTime"
    const val KEY_ALLOW_PANEL = "allowPanel"
    const val KEY_BLOCK_HOME = "blockHome"
    const val KEY_BLOCK_RECENT = "blockRecent"
    const val KEY_WHITELIST = "whitelist"

    // Notification
    const val NOTIF_CHANNEL_ID = "zen_sposed_session"
    const val NOTIF_ID = 4201

    // Intent actions
    const val ACTION_START_FOCUS = "com.hassan.zensposed.action.START_FOCUS"
    const val ACTION_STOP_FOCUS = "com.hassan.zensposed.action.STOP_FOCUS"
    const val ACTION_TICK = "com.hassan.zensposed.action.TICK"
    /** App → system_server: apply StatusBarManagerService.disable / disable2 flags. */
    const val ACTION_SET_PANEL_LOCKED = "com.hassan.zensposed.action.SET_PANEL_LOCKED"
    const val EXTRA_PANEL_LOCKED = "panel_locked"
    const val EXTRA_BLOCK_HOME = "block_home"
    const val EXTRA_BLOCK_RECENT = "block_recent"

    // Intent extras
    const val EXTRA_DURATION_MINUTES = "duration_minutes"
    const val EXTRA_SPACE_NAME = "space_name"
    const val EXTRA_THEME_ID = "theme_id"
    const val EXTRA_WHITELIST = "whitelist_csv"
    const val EXTRA_SHOW_CONGRATS = "show_congrats"
    const val EXTRA_FOCUSED_MS = "focused_ms"

    // Always-allowed packages (dialer / messaging / phone core)
    val ALWAYS_ALLOWED = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.android.messaging"
    )

    /**
     * System UI used transiently by whitelisted apps (share sheet, file picker, etc.).
     * Always allowed during a session so cross-app flows keep working.
     */
    val SESSION_SYSTEM_UI = setOf(
        "com.android.intentresolver",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.vpndialogs",
        "com.android.captiveportallogin"
    )
}
