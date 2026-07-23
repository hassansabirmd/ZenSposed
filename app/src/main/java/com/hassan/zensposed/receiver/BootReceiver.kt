package com.hassan.zensposed.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hassan.zensposed.focus.FocusService
import com.hassan.zensposed.focus.FocusState

/**
 * On boot, if a session was still active we relaunch the enforcement service so the
 * focus screen re-appears as soon as the device is unlocked.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            if (FocusState.isActive(context)) {
                val svc = Intent(context, FocusService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            }
        }
    }
}
