package com.hassan.zensposed.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hassan.zensposed.focus.FocusState
import com.hassan.zensposed.ui.focus.FocusActivity

/**
 * Registered dynamically by FocusService. When the user unlocks during an active
 * session, bring the focus screen back. We intentionally ignore SCREEN_ON alone
 * so pressing power to turn the screen off is not fought.
 */
class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return
        if (!FocusState.isActive(context)) return
        context.startActivity(
            Intent(context, FocusActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        )
    }
}
