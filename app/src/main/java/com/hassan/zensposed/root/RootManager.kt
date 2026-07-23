package com.hassan.zensposed.root

import android.util.Log
import com.hassan.zensposed.core.Constants
import com.topjohnwu.superuser.Shell

/**
 * Privileged (su) actions. ZenSposed requires working root — callers should
 * gate on PrivilegeRequirements before starting a session.
 */
object RootManager {

    private const val TAG = "ZenSposed/Root"

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    fun isRootAvailable(): Boolean = try {
        Shell.getShell().isRoot
    } catch (t: Throwable) {
        Log.w(TAG, "Root check failed", t)
        false
    }

    /** Magisk / KernelSU LSPosed framework present under /data/adb. */
    fun isLsposedFrameworkPresent(): Boolean {
        if (!isRootAvailable()) return false
        return try {
            val res = Shell.cmd(
                "if [ -d /data/adb/lspd ] || " +
                    "ls -d /data/adb/modules/*[Ll][Ss][Pp]osed* >/dev/null 2>&1 || " +
                    "ls -d /data/adb/modules/zygisk_lsposed >/dev/null 2>&1; " +
                    "then echo yes; else echo no; fi"
            ).exec()
            res.out.any { it.trim() == "yes" }
        } catch (t: Throwable) {
            Log.w(TAG, "LSPosed framework check failed", t)
            false
        }
    }

    /** True when the system_server hook has written [Constants.XPOSED_ALIVE_FILE]. */
    fun isXposedModuleAlive(): Boolean {
        if (!isRootAvailable()) return false
        return try {
            val res = Shell.cmd("cat ${Constants.XPOSED_ALIVE_FILE} 2>/dev/null || true").exec()
            val ts = res.out.firstOrNull()?.trim()?.toLongOrNull() ?: return false
            ts > 0L
        } catch (t: Throwable) {
            Log.w(TAG, "Xposed alive check failed", t)
            false
        }
    }

    private fun run(vararg cmds: String): Boolean {
        return try {
            val res = Shell.cmd(*cmds).exec()
            if (!res.isSuccess) Log.w(TAG, "cmd failed: ${cmds.joinToString(";")} -> ${res.err}")
            res.isSuccess
        } catch (t: Throwable) {
            Log.w(TAG, "cmd threw: ${cmds.joinToString(";")}", t)
            false
        }
    }

    /** Persist live session state where the LSPosed module (system processes) can read it. */
    fun writeBridge(
        locked: Boolean,
        endTime: Long,
        allowPanel: Boolean,
        blockHome: Boolean,
        blockRecent: Boolean,
        whitelist: Set<String>
    ) {
        val body = buildString {
            append("${Constants.KEY_LOCKED}=$locked\n")
            append("${Constants.KEY_END_TIME}=$endTime\n")
            append("${Constants.KEY_ALLOW_PANEL}=$allowPanel\n")
            append("${Constants.KEY_BLOCK_HOME}=$blockHome\n")
            append("${Constants.KEY_BLOCK_RECENT}=$blockRecent\n")
            append("${Constants.KEY_WHITELIST}=${whitelist.joinToString(",")}\n")
        }
        // heredoc write via su, then make world-readable AND relabel to a context that
        // system_server / SystemUI (where the LSPosed hooks run) can read under enforcing
        // SELinux. Files in /data/local/tmp default to shell_data_file which those domains
        // cannot read; system_data_file is broadly readable by system processes.
        run(
            "mkdir -p ${Constants.BRIDGE_DIR}",
            "cat > ${Constants.BRIDGE_FILE} <<'FLEOF'\n$body\nFLEOF",
            "chmod 755 ${Constants.BRIDGE_DIR}",
            "chmod 644 ${Constants.BRIDGE_FILE}",
            "chcon u:object_r:system_data_file:s0 ${Constants.BRIDGE_DIR} 2>/dev/null || true",
            "chcon u:object_r:system_data_file:s0 ${Constants.BRIDGE_FILE} 2>/dev/null || true"
        )
    }

    fun clearBridge() {
        writeBridge(
            locked = false,
            endTime = 0L,
            allowPanel = true,
            blockHome = false,
            blockRecent = false,
            whitelist = emptySet()
        )
    }

    /** Make the app immune to Doze / battery optimization and background limits. */
    fun applyKeepAlive() {
        val pkg = Constants.PACKAGE_NAME
        run(
            "dumpsys deviceidle whitelist +$pkg",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "settings put global app_standby_enabled 0",
            "cmd deviceidle disable"
        )
        // OEM-specific protected-app / auto-start (best effort, ignored where absent).
        run("dumpsys deviceidle whitelist +$pkg")
    }

    fun releaseKeepAlive() {
        val pkg = Constants.PACKAGE_NAME
        run(
            "dumpsys deviceidle whitelist -$pkg",
            "cmd deviceidle enable"
        )
    }

    /**
     * Secondary shell reinforce for expand-disable. Primary path is the
     * system_server LSPosed StatusBarManagerService.disable hook.
     */
    fun setStatusBarExpandDisabled(disabled: Boolean) {
        if (disabled) {
            // DISABLE_EXPAND=0x10000; disable2 QS|NOTIFICATION_SHADE = 1|4 = 5
            run(
                "service call statusbar 1 i32 65536 2>/dev/null || true",
                "cmd statusbar disable expand 2>/dev/null || true"
            )
        } else {
            run(
                "service call statusbar 1 i32 0 2>/dev/null || true",
                "cmd statusbar disable none 2>/dev/null || true"
            )
        }
    }

    /** Package name of the current resumed/top activity, or null. */
    fun topResumedPackage(): String? {
        return try {
            val res = Shell.cmd(
                "dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity|mResumedActivity' || true"
            ).exec()
            val line = res.out.firstOrNull() ?: return null
            // Formats like: topResumedActivity=ActivityRecord{... com.pkg/.Act t123}
            val regex = Regex("""\s([a-zA-Z0-9._]+)\/""")
            regex.find(line)?.groupValues?.getOrNull(1)
        } catch (_: Throwable) {
            null
        }
    }

    /** Prevent uninstall/force-stop at the package-manager level while locked. */
    fun setUninstallBlocked(blocked: Boolean) {
        val pkg = Constants.PACKAGE_NAME
        if (blocked) {
            run("pm disable-user --user 0 --uninstall-blocked $pkg 2>/dev/null || true")
        }
    }

    fun forceLaunchFocus() {
        run("am start -n ${Constants.PACKAGE_NAME}/.ui.focus.FocusActivity --activity-single-top")
    }

    /** Force-stop apps that were opened during the session so they don't linger. */
    fun forceStopPackages(packages: Set<String>) {
        if (packages.isEmpty()) return
        val cmds = packages.map { "am force-stop $it" }.toTypedArray()
        run(*cmds)
    }

    // ---- Quick-toggle actions (used from the focus screen when allowed) ----

    fun setWifi(enabled: Boolean) = run("svc wifi ${if (enabled) "enable" else "disable"}")

    fun setMobileData(enabled: Boolean) = run("svc data ${if (enabled) "enable" else "disable"}")

    fun setHotspot(enabled: Boolean) {
        // Toggling the soft-AP programmatically is OEM-specific; try the modern cmd path
        // and fall back to the tethering service call. Best-effort under root.
        if (enabled) {
            run("cmd wifi start-softap ZenSposed open 2>/dev/null || svc wifi enable")
        } else {
            run("cmd wifi stop-softap 2>/dev/null || true")
        }
    }

    /** zen_mode: 0=off, 1=priority, 2=total silence, 3=alarms only */
    fun setDnd(enabled: Boolean): Boolean = run(
        if (enabled) {
            "cmd notification set_dnd priority 2>/dev/null || settings put global zen_mode 1"
        } else {
            "cmd notification set_dnd off 2>/dev/null || settings put global zen_mode 0"
        }
    )

    /**
     * Clear other apps from Recents without bringing the launcher forward.
     * Never removes [excludePkg] tasks and avoids `clear-recent-apps` (that flashes Home).
     */
    fun clearRecentApps(excludePkg: String = Constants.PACKAGE_NAME): Boolean {
        return try {
            // Hold ZenSposed on top before removals so Home never becomes visible.
            val keepFront = "am start -n $excludePkg/.ui.focus.FocusActivity " +
                "--activity-single-top --activity-reorder-to-front 2>/dev/null || true"
            run(keepFront)

            val dump = Shell.cmd("dumpsys activity recents 2>/dev/null || dumpsys activity activities 2>/dev/null || true")
                .exec().out.joinToString("\n")
            val taskIds = LinkedHashSet<String>()
            val idRegex = Regex("""(?:taskId|id)=(\d+)""")
            val lines = dump.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val idMatch = idRegex.find(line)
                if (idMatch != null) {
                    val id = idMatch.groupValues[1]
                    val window = lines.subList(i, minOf(i + 8, lines.size)).joinToString(" ")
                    val belongsToUs = window.contains(excludePkg)
                    val looksLikeTask = window.contains('/') || window.contains("ActivityRecord") ||
                        window.contains("Task{") || window.contains("taskId")
                    if (!belongsToUs && looksLikeTask) taskIds.add(id)
                }
                i++
            }
            if (taskIds.isEmpty()) {
                idRegex.findAll(dump).forEach { m ->
                    val id = m.groupValues[1]
                    val start = (m.range.first - 120).coerceAtLeast(0)
                    val end = (m.range.last + 120).coerceAtMost(dump.length)
                    val ctx = dump.substring(start, end)
                    if (!ctx.contains(excludePkg)) taskIds.add(id)
                }
            }
            if (taskIds.isEmpty()) return true
            val cmds = taskIds.take(40).map {
                "am task remove $it 2>/dev/null || cmd activity remove-task $it 2>/dev/null || true"
            }
            // Re-assert front after removals without relying on Home becoming top.
            run(*(cmds + keepFront).toTypedArray())
        } catch (t: Throwable) {
            Log.w(TAG, "clearRecentApps failed", t)
            false
        }
    }

    fun setBatterySaver(enabled: Boolean): Boolean = run(
        if (enabled) {
            "settings put global low_power 1; cmd power set-mode 1 2>/dev/null || true"
        } else {
            "settings put global low_power 0; cmd power set-mode 0 2>/dev/null || true"
        }
    )

    fun isBatterySaverOn(): Boolean = try {
        val res = Shell.cmd("settings get global low_power 2>/dev/null || echo 0").exec()
        res.out.firstOrNull()?.trim() == "1"
    } catch (_: Throwable) {
        false
    }
}
