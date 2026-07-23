package com.hassan.zensposed.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.hassan.zensposed.root.RootManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks Wi‑Fi radio on/off for focus quick toggles.
 *
 * On recent Pixel builds, [Settings.Global.WIFI_ON] via ContentResolver and even
 * [WifiManager.isWifiEnabled] can be stale or wrong for third-party apps, while
 * the sticky [WifiManager.WIFI_STATE_CHANGED_ACTION] broadcast and root
 * `settings get global wifi_on` stay accurate. Broadcasts update immediately;
 * root is used as a poll fallback.
 */
object WifiStateTracker {

    private val started = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var lastUpdateElapsed: Long = 0L

    fun isOn(): Boolean = enabled

    fun ensureObserving(context: Context) {
        if (!started.compareAndSet(false, true)) {
            // Still refresh once in case we missed a change while not composed.
            refresh(context.applicationContext)
            return
        }
        val app = context.applicationContext
        enabled = readWifiEnabled(app)
        lastUpdateElapsed = SystemClock.elapsedRealtime()

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
        // Sticky intent often carries the current state at registration time.
        refresh(app)
    }

    fun addListener(listener: (Boolean) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Re-read system state (for polls / resume). */
    fun refresh(context: Context) {
        RootManager.clearWifiCache()
        publish(readWifiEnabled(context.applicationContext))
    }

    private fun publish(on: Boolean) {
        enabled = on
        lastUpdateElapsed = SystemClock.elapsedRealtime()
        RootManager.noteWifiEnabled(on)
        listeners.forEach { it(on) }
    }

    /**
     * Cascade: root settings/dumpsys (most accurate on Pixel) → sticky broadcast →
     * WifiManager.wifiState. Avoids ContentResolver [Settings.Global.WIFI_ON], which
     * can read as 0 and was overwriting correct UI every poll.
     */
    fun readWifiEnabled(context: Context): Boolean {
        RootManager.readWifiEnabled()?.let { return it }
        stickyWifiEnabled(context)?.let { return it }
        wifiManagerState(context)?.let { return it }
        return enabled
    }

    private fun stickyWifiEnabled(context: Context): Boolean? {
        return try {
            val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            val sticky = if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(null, filter)
            } ?: return null
            when (
                sticky.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
            ) {
                WifiManager.WIFI_STATE_ENABLED,
                WifiManager.WIFI_STATE_ENABLING -> true
                WifiManager.WIFI_STATE_DISABLED,
                WifiManager.WIFI_STATE_DISABLING -> false
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun wifiManagerState(context: Context): Boolean? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            when (wm.wifiState) {
                WifiManager.WIFI_STATE_ENABLED,
                WifiManager.WIFI_STATE_ENABLING -> true
                WifiManager.WIFI_STATE_DISABLED,
                WifiManager.WIFI_STATE_DISABLING -> false
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
