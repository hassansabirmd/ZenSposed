package com.hassan.zensposed.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.hassan.zensposed.root.RootManager

/** Do Not Disturb helper — NotificationManager when permitted, root fallback otherwise. */
object DndController {

    fun isOn(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Throwable) {
            false
        }
    }

    fun set(context: Context, enabled: Boolean): Boolean {
        // Prefer the public API when we have policy access.
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(
                    if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
                return true
            }
        } catch (_: Throwable) {
        }
        // Root / shell fallback (works without the settings grant on rooted devices).
        return RootManager.setDnd(enabled)
    }

    fun openPolicySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
