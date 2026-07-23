package com.hassan.zensposed.lock

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hassan.zensposed.admin.FocusDeviceAdminReceiver
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.root.RootManager

/**
 * Coordinates every enforcement layer when a session starts / stops:
 * root keep-alive + bridge, device-admin uninstall blocking, status-bar disable
 * broadcast for the system_server StatusBarManagerService hook.
 */
object LockEnforcer {

    private const val TAG = "ZenSposed/Lock"

    fun onSessionStart(
        context: Context,
        endTime: Long,
        allowPanel: Boolean,
        blockHome: Boolean,
        blockRecent: Boolean,
        whitelist: Set<String>
    ) {
        RootManager.applyKeepAlive()
        RootManager.writeBridge(
            locked = true,
            endTime = endTime,
            allowPanel = allowPanel,
            blockHome = blockHome,
            blockRecent = blockRecent,
            whitelist = whitelist
        )
        RootManager.setUninstallBlocked(true)
        broadcastStatusBarFlags(
            context,
            sessionActive = true,
            allowPanel = allowPanel,
            blockHome = blockHome,
            blockRecent = blockRecent
        )
        RootManager.setStatusBarExpandDisabled(!allowPanel)
        blockUninstallViaAdmin(context, true)
    }

    fun updateBridge(
        context: Context,
        endTime: Long,
        allowPanel: Boolean,
        blockHome: Boolean,
        blockRecent: Boolean,
        whitelist: Set<String>
    ) {
        RootManager.writeBridge(
            locked = true,
            endTime = endTime,
            allowPanel = allowPanel,
            blockHome = blockHome,
            blockRecent = blockRecent,
            whitelist = whitelist
        )
        broadcastStatusBarFlags(
            context,
            sessionActive = true,
            allowPanel = allowPanel,
            blockHome = blockHome,
            blockRecent = blockRecent
        )
        RootManager.setStatusBarExpandDisabled(!allowPanel)
    }

    fun onSessionStop(context: Context) {
        broadcastStatusBarFlags(
            context,
            sessionActive = false,
            allowPanel = true,
            blockHome = false,
            blockRecent = false
        )
        RootManager.setStatusBarExpandDisabled(false)
        RootManager.clearBridge()
        RootManager.releaseKeepAlive()
        RootManager.setUninstallBlocked(false)
        blockUninstallViaAdmin(context, false)
    }

    fun broadcastStatusBarFlags(
        context: Context,
        sessionActive: Boolean,
        allowPanel: Boolean,
        blockHome: Boolean,
        blockRecent: Boolean
    ) {
        try {
            val panelLocked = sessionActive && !allowPanel
            val homeBlocked = sessionActive && blockHome
            val recentBlocked = sessionActive && blockRecent
            val intent = Intent(Constants.ACTION_SET_PANEL_LOCKED).apply {
                putExtra(Constants.EXTRA_PANEL_LOCKED, panelLocked)
                putExtra(Constants.EXTRA_BLOCK_HOME, homeBlocked)
                putExtra(Constants.EXTRA_BLOCK_RECENT, recentBlocked)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(intent)
            Log.i(
                TAG,
                "status-bar flags: panelLocked=$panelLocked blockHome=$homeBlocked blockRecent=$recentBlocked"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "status-bar flags broadcast failed", t)
        }
    }

    private fun blockUninstallViaAdmin(context: Context, blocked: Boolean) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = FocusDeviceAdminReceiver.component(context)
            if (!dpm.isAdminActive(admin)) return
            val owner = dpm.isDeviceOwnerApp(Constants.PACKAGE_NAME) ||
                dpm.isProfileOwnerApp(Constants.PACKAGE_NAME)
            if (owner) {
                dpm.setUninstallBlocked(admin, Constants.PACKAGE_NAME, blocked)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "admin uninstall block failed", t)
        }
    }
}
