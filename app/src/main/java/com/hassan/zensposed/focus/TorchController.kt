package com.hassan.zensposed.focus

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Torch control via CameraManager (the reliable path). Root shell flashlight cmds
 * are OEM-specific and often no-ops on Pixel / AOSP.
 *
 * Also observes system torch state (Quick Settings, other apps) via [TorchCallback]
 * so focus-screen icons stay in sync.
 */
object TorchController {

    @Volatile
    private var enabled: Boolean = false

    private val started = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun isOn(): Boolean = enabled

    /** Start observing system torch changes. Safe to call repeatedly. */
    fun ensureObserving(context: Context) {
        if (!started.compareAndSet(false, true)) return
        try {
            val cm = context.applicationContext.getSystemService(CameraManager::class.java) ?: return
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, on: Boolean) {
                        enabled = on
                        listeners.forEach { it(on) }
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        enabled = false
                        listeners.forEach { it(false) }
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (_: Throwable) {
            started.set(false)
        }
    }

    /** Listen for torch on/off changes. Returns an unregister callback. */
    fun addListener(listener: (Boolean) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun set(context: Context, on: Boolean): Boolean {
        ensureObserving(context)
        return try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return false
            val id = cm.cameraIdList.firstOrNull { camId ->
                val chars = cm.getCameraCharacteristics(camId)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(id, on)
            enabled = on
            listeners.forEach { it(on) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun toggle(context: Context): Boolean {
        val next = !enabled
        return if (set(context, next)) next else enabled
    }
}
