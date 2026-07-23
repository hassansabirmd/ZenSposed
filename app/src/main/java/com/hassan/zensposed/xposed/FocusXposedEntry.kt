package com.hassan.zensposed.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.hassan.zensposed.core.Constants
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * LSPosed entry point. When a session is active:
 *  - Panel expand is disabled via StatusBarManagerService inside system_server
 *    (broadcast from the app — client-process StatusBarManager.disable() cannot work)
 *  - Non-whitelisted activity starts are vetoed in system_server
 *  - Uninstall / force-stop of ZenSposed itself is blocked
 */
class FocusXposedEntry : IXposedHookLoadPackage {

    companion object {
        // StatusBarManager disable flags
        private const val DISABLE_NONE = 0
        private const val DISABLE_EXPAND = 0x00010000
        private const val DISABLE_NOTIFICATION_ICONS = 0x00020000
        private const val DISABLE_NOTIFICATION_ALERTS = 0x00040000
        private const val DISABLE_HOME = 0x00200000
        private const val DISABLE_RECENT = 0x01000000
        private const val DISABLE_PANEL_FLAGS =
            DISABLE_EXPAND or DISABLE_NOTIFICATION_ICONS or DISABLE_NOTIFICATION_ALERTS

        // StatusBarManager disable2 flags
        private const val DISABLE2_NONE = 0
        private const val DISABLE2_QUICK_SETTINGS = 1
        private const val DISABLE2_NOTIFICATION_SHADE = 1 shl 2
        private const val DISABLE2_PANEL_FLAGS =
            DISABLE2_QUICK_SETTINGS or DISABLE2_NOTIFICATION_SHADE

        private val PANEL_TOKEN: IBinder = Binder()

        private data class DisableSnapshot(
            val panelLocked: Boolean,
            val blockHome: Boolean,
            val blockRecent: Boolean
        )

        @Volatile private var statusBarService: Any? = null
        @Volatile private var panelReceiverRegistered = false
        @Volatile private var lastFlags: DisableSnapshot? = null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "com.android.settings" -> hookSettings(lpparam)
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller" -> hookPackageInstaller(lpparam)
            "android" -> hookSystemServer(lpparam)
        }
    }

    private fun log(msg: String) = XposedBridge.log("ZenSposed: $msg")

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        markXposedAlive()
        hookStatusBarManagerService(lpparam)

        try {
            val amsClass = XposedHelpers.findClass(
                "com.android.server.am.ActivityManagerService",
                lpparam.classLoader
            )
            XposedBridge.hookAllMethods(amsClass, "forceStopPackage", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val pkg = param.args.getOrNull(0) as? String ?: return
                    val state = BridgeState.read()
                    if (state.locked && pkg == Constants.PACKAGE_NAME) {
                        param.result = null
                    }
                }
            })
            log("system_server forceStop guard installed")
        } catch (t: Throwable) {
            log("forceStop hook failed: ${t.message}")
        }

        try {
            val pmsClass = XposedHelpers.findClass(
                "com.android.server.pm.PackageManagerService",
                lpparam.classLoader
            )
            XposedBridge.hookAllMethods(pmsClass, "deletePackageVersioned", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val state = BridgeState.read()
                    if (!state.locked) return
                    val versioned = param.args.getOrNull(0)
                    val pkg = try {
                        XposedHelpers.getObjectField(versioned, "mPackageName") as? String
                    } catch (_: Throwable) {
                        versioned?.toString()
                    }
                    if (pkg == Constants.PACKAGE_NAME) {
                        param.result = null
                    }
                }
            })
            log("system_server delete guard installed")
        } catch (t: Throwable) {
            log("delete hook failed: ${t.message}")
        }

        hookActivityStart(lpparam)
    }

    /**
     * Guide-aligned panel lock: StatusBarManagerService.disable / disable2 from
     * system_server only. Triggered by app broadcast + bridge-file sync.
     */
    private fun hookStatusBarManagerService(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val serviceClass = XposedHelpers.findClass(
                "com.android.server.statusbar.StatusBarManagerService",
                lpparam.classLoader
            )
            XposedBridge.hookAllConstructors(serviceClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val service = param.thisObject
                    val context = param.args.firstOrNull { it is Context } as? Context
                        ?: systemContext(lpparam.classLoader)
                    if (context == null) {
                        log("StatusBarManagerService ctor: no Context")
                        return
                    }
                    attachPanelLock(context, service)
                }
            })
            log("StatusBarManagerService constructor hooked")
        } catch (t: Throwable) {
            log("StatusBarManagerService hook failed: ${t.message}")
        }

        // If the service was already created before our ctor hook ran (module reload),
        // attach via ServiceManager after system_server settles.
        Handler(Looper.getMainLooper()).postDelayed({
            if (statusBarService != null) return@postDelayed
            try {
                val smClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader)
                val binder = XposedHelpers.callStaticMethod(smClass, "getService", "statusbar")
                    ?: return@postDelayed
                val context = systemContext(lpparam.classLoader) ?: return@postDelayed
                attachPanelLock(context, binder)
                log("StatusBarManagerService attached via ServiceManager fallback")
            } catch (t: Throwable) {
                log("ServiceManager statusbar fallback failed: ${t.message}")
            }
        }, 8000L)
    }

    private fun attachPanelLock(context: Context, service: Any) {
        statusBarService = service
        markXposedAlive()
        registerPanelLockReceiver(context, service)
        startBridgePanelSync(service)
        syncPanelFromBridge(service)
        log("StatusBarManagerService panel lock ready")
    }

    /** Prove to the app that system_server hooks are loaded (required privilege). */
    private fun markXposedAlive() {
        try {
            val f = File(Constants.XPOSED_ALIVE_FILE)
            f.parentFile?.mkdirs()
            f.writeText(System.currentTimeMillis().toString())
            log("xposed_alive written")
        } catch (t: Throwable) {
            log("xposed_alive write failed: ${t.message}")
        }
    }

    private fun systemContext(classLoader: ClassLoader): Context? {
        return try {
            val atClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
            val at = XposedHelpers.callStaticMethod(atClass, "currentActivityThread")
            XposedHelpers.callMethod(at, "getSystemContext") as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private fun registerPanelLockReceiver(context: Context, service: Any) {
        if (panelReceiverRegistered) return
        val filter = IntentFilter(Constants.ACTION_SET_PANEL_LOCKED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Constants.ACTION_SET_PANEL_LOCKED) return
                val panelLocked = intent.getBooleanExtra(Constants.EXTRA_PANEL_LOCKED, false)
                val blockHome = intent.getBooleanExtra(Constants.EXTRA_BLOCK_HOME, false)
                val blockRecent = intent.getBooleanExtra(Constants.EXTRA_BLOCK_RECENT, false)
                applyStatusBarDisable(service, panelLocked, blockHome, blockRecent, "broadcast")
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            panelReceiverRegistered = true
            log("panel lock broadcast receiver registered")
        } catch (t: Throwable) {
            log("registerReceiver failed: ${t.message}")
        }
    }

    /** Backup if a broadcast is missed (boot, process race). */
    private fun startBridgePanelSync(service: Any) {
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                markXposedAlive()
                syncPanelFromBridge(service)
                handler.postDelayed(this, 2000L)
            }
        }
        handler.postDelayed(tick, 1500L)
    }

    private fun syncPanelFromBridge(service: Any) {
        try {
            val state = BridgeState.read()
            val panelLocked = state.locked && !state.allowPanel
            val blockHome = state.locked && state.blockHome
            val blockRecent = state.locked && state.blockRecent
            val want = DisableSnapshot(panelLocked, blockHome, blockRecent)
            if (lastFlags != want) {
                applyStatusBarDisable(service, panelLocked, blockHome, blockRecent, "bridge")
            }
        } catch (t: Throwable) {
            log("bridge panel sync failed: ${t.message}")
        }
    }

    private fun applyStatusBarDisable(
        service: Any,
        panelLocked: Boolean,
        blockHome: Boolean,
        blockRecent: Boolean,
        source: String
    ) {
        try {
            var flags1 = DISABLE_NONE
            var flags2 = DISABLE2_NONE
            if (panelLocked) {
                flags1 = flags1 or DISABLE_PANEL_FLAGS
                flags2 = flags2 or DISABLE2_PANEL_FLAGS
            }
            if (blockHome) flags1 = flags1 or DISABLE_HOME
            if (blockRecent) flags1 = flags1 or DISABLE_RECENT
            XposedHelpers.callMethod(
                service, "disable", flags1, PANEL_TOKEN, Constants.PACKAGE_NAME
            )
            XposedHelpers.callMethod(
                service, "disable2", flags2, PANEL_TOKEN, Constants.PACKAGE_NAME
            )
            lastFlags = DisableSnapshot(panelLocked, blockHome, blockRecent)
            log(
                "status-bar disable ($source): panel=$panelLocked home=$blockHome recent=$blockRecent " +
                    "flags1=0x${Integer.toHexString(flags1)} flags2=$flags2"
            )
        } catch (t: Throwable) {
            log("StatusBarManagerService.disable failed ($source): ${t.message}")
        }
    }

    private fun hookSettings(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)
            XposedBridge.hookAllMethods(activityClass, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val state = BridgeState.read()
                    if (!state.locked) return
                    val activity = param.thisObject as android.app.Activity
                    val intent = activity.intent ?: return
                    val data = intent.data?.toString() ?: ""
                    if (data.contains(Constants.PACKAGE_NAME)) {
                        activity.finish()
                    }
                }
            })
            log("Settings app-info guard installed")
        } catch (t: Throwable) {
            log("Settings hook failed: ${t.message}")
        }
    }

    private fun hookPackageInstaller(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)
            XposedBridge.hookAllMethods(activityClass, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val state = BridgeState.read()
                    if (!state.locked) return
                    val activity = param.thisObject as android.app.Activity
                    val intent = activity.intent ?: return
                    val data = intent.data?.toString() ?: ""
                    val extra = intent.getStringExtra("android.intent.extra.PACKAGE_NAME") ?: ""
                    if (data.contains(Constants.PACKAGE_NAME) || extra == Constants.PACKAGE_NAME) {
                        activity.finish()
                    }
                }
            })
            log("PackageInstaller uninstall guard installed")
        } catch (t: Throwable) {
            log("PackageInstaller hook failed: ${t.message}")
        }
    }

    private fun hookActivityStart(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.android.server.wm.ActivityTaskManagerService",
            "com.android.server.am.ActivityManagerService"
        )
        for (className in candidates) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                XposedBridge.hookAllMethods(clazz, "startActivity", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        maybeBlockStart(param)
                    }
                })
                XposedBridge.hookAllMethods(clazz, "startActivityAsUser", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        maybeBlockStart(param)
                    }
                })
                log("$className activity-start hooks installed")
                return
            } catch (_: Throwable) {
            }
        }
        log("activity-start hooks NOT found")
    }

    private fun maybeBlockStart(param: XC_MethodHook.MethodHookParam) {
        val state = BridgeState.read()
        if (!state.locked) return

        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
        val pkg = intent.component?.packageName ?: intent.`package` ?: return
        val action = intent.action.orEmpty()

        if (pkg == Constants.PACKAGE_NAME) return
        if (pkg == "com.android.systemui") return
        if (pkg.contains("permissioncontroller", ignoreCase = true)) return
        if (pkg.contains("keyguard", ignoreCase = true)) return
        // Share sheet / docs / installer mid-flows used by whitelisted apps.
        if (pkg in Constants.SESSION_SYSTEM_UI) return
        if (isTransientSystemUiPkg(pkg)) return
        // Soft keyboards (Gboard etc.) must not be blocked while typing.
        if (pkg in Constants.SESSION_IME || isInputMethodPkg(pkg)) return
        // System Wi‑Fi / connectivity panels opened from the focus screen.
        if (action.startsWith("android.settings.panel.") ||
            action == "android.settings.WIFI_SETTINGS" ||
            action == "android.settings.panel.action.WIFI"
        ) return
        if (pkg == "com.android.settings" &&
            (action.contains("WIFI", ignoreCase = true) || action.contains("panel", ignoreCase = true))
        ) return
        // Framework chooser on older builds.
        if (pkg == "android" &&
            (action == Intent.ACTION_CHOOSER || action == Intent.ACTION_PICK ||
                action == Intent.ACTION_GET_CONTENT)
        ) return

        val alwaysOk = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.mms"
        )
        if (pkg in alwaysOk) return
        if (pkg in state.whitelist) return

        param.result = -96 // ActivityManager.START_CANCELED
    }

    private fun isTransientSystemUiPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("intentresolver") ||
            lower.contains("documentsui") ||
            lower.contains("permissioncontroller") ||
            lower.endsWith(".packageinstaller")
    }

    private fun isInputMethodPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") ||
            lower.contains("honeyboard") ||
            lower.contains("swiftkey") ||
            (lower.contains("keyboard") && !lower.contains("settings"))
    }
}
