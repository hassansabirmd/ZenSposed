package com.hassan.zensposed.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.hassan.zensposed.core.AppResolver
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.focus.FocusState
import com.hassan.zensposed.ui.focus.FocusActivity

/**
 * Watchdog: while a session is active, bounce non-whitelisted apps (including the
 * launcher after leaving a whitelisted app). Panel blocking is handled by the
 * LSPosed StatusBarManager path — not by collapsing the shade here.
 */
class FocusAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val session = FocusState.current(this) ?: return
        if (!session.active) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (FocusActivity.isReassertSuppressed()) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == Constants.PACKAGE_NAME) return
        if (pkg == "com.android.systemui") return
        if (pkg.contains("keyguard", ignoreCase = true)) return
        if (AppResolver.isTransientSystemUi(pkg)) return

        val allowed = session.whitelist + AppResolver.alwaysAllowedPackages(this)

        if (pkg.contains("packageinstaller") || pkg == "com.android.settings") {
            val text = event.text?.joinToString(" ")?.lowercase() ?: ""
            if (text.contains("zensposed") || text.contains("zen sposed") || text.contains("focus lock") || text.contains("uninstall")) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                bringFocusToFront()
                return
            }
            // Settings panels (e.g. Wi‑Fi picker) while temporarily suppressed are handled above.
            if (pkg == "com.android.settings" && FocusActivity.isReassertSuppressed()) return
        }

        if (pkg !in allowed) {
            bringFocusToFront()
        }
    }

    private fun bringFocusToFront() {
        startActivity(
            Intent(this, FocusActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        )
    }

    override fun onInterrupt() {}
}
