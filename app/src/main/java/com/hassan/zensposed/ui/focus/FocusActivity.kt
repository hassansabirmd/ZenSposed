package com.hassan.zensposed.ui.focus

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hassan.zensposed.ZenSposedApp
import com.hassan.zensposed.focus.FocusController
import com.hassan.zensposed.focus.FocusState
import com.hassan.zensposed.ui.theme.ZenSposedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full-screen focus UI. Screen pinning is NOT used (always user-reversible).
 * Panel / home / recents blocking is handled by LSPosed StatusBarManagerService hooks.
 *
 * When Always-On Display is enabled, the screen stays on at low brightness and
 * dimms to a timer-only view after idle (pseudo-AOD).
 */
class FocusActivity : ComponentActivity() {

    /** When set, onStop will not immediately re-launch us (user opened a whitelisted app). */
    @Volatile
    var suppressReassertUntilMs: Long = 0L

    private var aodEnabled by mutableStateOf(false)
    private var aodDimmed by mutableStateOf(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable {
        if (aodEnabled && FocusState.isActive(this)) {
            aodDimmed = true
            applyAodBrightness(dimmed = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!FocusState.isActive(this@FocusActivity)) {
                isEnabled = false
                finish()
            }
        }

        lifecycleScope.launch {
            val settings = ZenSposedApp.instance.settingsRepository.settings.first()
            aodEnabled = settings.aodEnabled
            applyAodMode(aodEnabled)
            if (aodEnabled) scheduleAodDim()
        }

        setContent {
            ZenSposedTheme(darkTheme = true) {
                val ui by FocusController.state.collectAsStateWithLifecycle()
                LaunchedEffect(ui.active) {
                    if (!ui.active && !FocusState.isActive(this@FocusActivity)) {
                        finish()
                    }
                }
                FocusScreen(
                    ui = ui,
                    aodEnabled = aodEnabled,
                    aodDimmed = aodDimmed,
                    onUserInteraction = { wakeFromAod() },
                    onExitConfirmed = { finishSession() },
                    onLaunchAllowedApp = { pkg -> launchAllowedApp(pkg) },
                    onOpenWifiPicker = { openWifiPicker() }
                )
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            wakeFromAod()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        if (!FocusState.isActive(this)) {
            finish()
            return
        }
        applyAodMode(aodEnabled)
        if (aodEnabled) scheduleAodDim()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        reassertIfNeeded()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(dimRunnable)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun applyAodMode(enabled: Boolean) {
        val attrs = window.attributes
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            applyAodBrightness(dimmed = aodDimmed)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
            aodDimmed = false
        }
    }

    private fun applyAodBrightness(dimmed: Boolean) {
        if (!aodEnabled) return
        val attrs = window.attributes
        // Pseudo-AOD: very dim when idle, slightly brighter when interacting.
        attrs.screenBrightness = if (dimmed) 0.02f else 0.12f
        window.attributes = attrs
    }

    private fun scheduleAodDim() {
        mainHandler.removeCallbacks(dimRunnable)
        if (!aodEnabled) return
        mainHandler.postDelayed(dimRunnable, AOD_IDLE_MS)
    }

    private fun wakeFromAod() {
        if (!aodEnabled) return
        aodDimmed = false
        applyAodBrightness(dimmed = false)
        scheduleAodDim()
    }

    private fun openWifiPicker() {
        wakeFromAod()
        suppressReassertFor(12_000L)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun launchAllowedApp(pkg: String) {
        wakeFromAod()
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        suppressReassertFor(1_500L)
        startActivity(intent)
    }

    private fun reassertIfNeeded() {
        if (!FocusState.isActive(this)) return
        if (isReassertSuppressed()) return
        window.decorView.postDelayed({
            if (!FocusState.isActive(this)) return@postDelayed
            if (isReassertSuppressed()) return@postDelayed
            startActivity(
                Intent(this, FocusActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
        }, 400L)
    }

    private fun finishSession() {
        mainHandler.removeCallbacks(dimRunnable)
        FocusController.stopFocus(this)
        finish()
    }

    companion object {
        private const val AOD_IDLE_MS = 20_000L

        @Volatile
        var instance: FocusActivity? = null

        fun isReassertSuppressed(): Boolean {
            val until = instance?.suppressReassertUntilMs ?: 0L
            return System.currentTimeMillis() < until
        }

        fun suppressReassertFor(ms: Long) {
            instance?.let {
                it.suppressReassertUntilMs = System.currentTimeMillis() + ms
            }
        }
    }
}
