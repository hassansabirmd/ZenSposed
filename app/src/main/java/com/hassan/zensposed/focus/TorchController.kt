package com.hassan.zensposed.focus

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Torch control via CameraManager (the reliable path). Root shell flashlight cmds
 * are OEM-specific and often no-ops on Pixel / AOSP.
 */
object TorchController {

    @Volatile
    private var enabled: Boolean = false

    fun isOn(): Boolean = enabled

    fun set(context: Context, on: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return false
            val id = cm.cameraIdList.firstOrNull { camId ->
                val chars = cm.getCameraCharacteristics(camId)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(id, on)
            enabled = on
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
