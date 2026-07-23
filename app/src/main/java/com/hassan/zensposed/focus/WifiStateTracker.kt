package com.hassan.zensposed.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks Wi‑Fi radio on/off for focus quick toggles.
 *
 * Source of truth is [WifiManager] ([WifiManager.getWifiState] / [WifiManager.isWifiEnabled]) —
 * the officially supported live read (only *setting* Wi‑Fi was restricted in Android 10).
 * The legacy `Settings.Global.WIFI_ON` value is NOT used: on modern Pixel builds it no
 * longer tracks the QS toggle (Wi‑Fi state persistence moved out of settings), which made
 * the icon stick in one state. [WifiManager.WIFI_STATE_CHANGED_ACTION] broadcasts provide
 * instant updates; callers may also poll [refresh].
 */
object WifiStateTracker {

    private val started = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    @Volatile
    private var enabled: Boolean = false

    fun isOn(): Boolean = enabled

    fun ensureObserving(context: Context) {
        if (!started.compareAndSet(false, true)) {
            // Still refresh once in case we missed a change while not composed.
            refresh(context.applicationContext)
            return
        }
        val app = context.applicationContext
        enabled = readWifiEnabled(app)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.WIFI_STATE_CHANGED_ACTION) return
                val state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )
                val on = when (state) {
                    WifiManager.WIFI_STATE_ENABLED,
                    WifiManager.WIFI_STATE_ENABLING -> true
                    WifiManager.WIFI_STATE_DISABLED,
                    WifiManager.WIFI_STATE_DISABLING -> false
                    else -> readWifiEnabled(app)
                }
                publish(on)
            }
        }
        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
        } catch (_: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    app.registerReceiver(receiver, filter)
                }
            } catch (_: Throwable) {
                started.set(false)
            }
        }
        refresh(app)
    }

    fun addListener(listener: (Boolean) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Re-read system state (for polls / resume). */
    fun refresh(context: Context) {
        publish(readWifiEnabled(context.applicationContext))
    }

    private fun publish(on: Boolean) {
        enabled = on
        listeners.forEach { it(on) }
    }

    /** Live Wi‑Fi radio state straight from [WifiManager]. */
    fun readWifiEnabled(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            when (wm.wifiState) {
                WifiManager.WIFI_STATE_ENABLED,
                WifiManager.WIFI_STATE_ENABLING -> true
                WifiManager.WIFI_STATE_DISABLED,
                WifiManager.WIFI_STATE_DISABLING -> false
                else -> @Suppress("DEPRECATION") wm.isWifiEnabled
            }
        } catch (_: Throwable) {
            enabled
        }
    }
}
