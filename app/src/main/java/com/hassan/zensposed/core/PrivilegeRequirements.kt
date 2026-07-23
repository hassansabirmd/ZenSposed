package com.hassan.zensposed.core

import com.hassan.zensposed.root.RootManager

/**
 * ZenSposed is root + LSPosed only. There is no unsupported "normal mode".
 */
object PrivilegeRequirements {

    data class Status(
        val root: Boolean,
        val lsposedFramework: Boolean,
        val xposedHooks: Boolean
    ) {
        val ready: Boolean get() = root && lsposedFramework && xposedHooks

        fun missingMessage(): String = buildString {
            append("ZenSposed requires Magisk/KernelSU (Zygisk), LSPosed, and root.")
            if (!root) append("\n• Superuser (su) is not available")
            if (!lsposedFramework) append("\n• LSPosed framework not detected")
            if (!xposedHooks) {
                append(
                    "\n• ZenSposed LSPosed hooks not loaded — enable the module for " +
                        "System Framework (android), then soft reboot"
                )
            }
        }
    }

    fun check(): Status = Status(
        root = RootManager.isRootAvailable(),
        lsposedFramework = RootManager.isLsposedFrameworkPresent(),
        xposedHooks = RootManager.isXposedModuleAlive()
    )
}
