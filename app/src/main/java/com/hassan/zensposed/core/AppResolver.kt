package com.hassan.zensposed.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.view.inputmethod.InputMethodManager

/**
 * Resolves Dialer / Messaging / Contacts / Calculator for the focus UI, plus the
 * broader telephony packages needed for enforcement (incall, telecom, etc.).
 */
object AppResolver {

    private var uiCache: Set<String>? = null
    private var enforceCache: Set<String>? = null
    private var imeCache: Set<String>? = null

    /**
     * The four default apps shown on the focus screen and greyed in the whitelist
     * picker (Dialer, Messaging, Contacts, Calculator). Only packages that actually
     * have a launcher entry.
     */
    fun defaultUiPackages(context: Context): Set<String> {
        uiCache?.let { return it }
        val pm = context.packageManager
        val result = LinkedHashSet<String>()

        runCatching {
            @Suppress("DEPRECATION")
            val tm = context.getSystemService(android.telecom.TelecomManager::class.java)
            tm?.defaultDialerPackage?.let { if (pm.getLaunchIntentForPackage(it) != null) result.add(it) }
        }
        if (result.none { it.contains("dialer", true) || it.contains("phone", true) }) {
            runCatching {
                val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                pm.resolveActivity(dial, 0)?.activityInfo?.packageName?.let {
                    if (it != "android" && pm.getLaunchIntentForPackage(it) != null) result.add(it)
                }
            }
        }

        runCatching {
            Telephony.Sms.getDefaultSmsPackage(context)?.let {
                if (pm.getLaunchIntentForPackage(it) != null) result.add(it)
            }
        }

        runCatching {
            val contacts = Intent(Intent.ACTION_VIEW).apply {
                type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
            }
            pm.resolveActivity(contacts, 0)?.activityInfo?.packageName?.let {
                if (it != "android" && pm.getLaunchIntentForPackage(it) != null) result.add(it)
            }
        }

        var calcAdded = false
        runCatching {
            val calc = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            pm.resolveActivity(calc, 0)?.activityInfo?.packageName?.let {
                if (it != "android" && pm.getLaunchIntentForPackage(it) != null) {
                    result.add(it); calcAdded = true
                }
            }
        }
        if (!calcAdded) {
            for (candidate in CALCULATOR_FALLBACKS) {
                if (pm.getLaunchIntentForPackage(candidate) != null) {
                    result.add(candidate)
                    break
                }
            }
        }

        uiCache = result
        return result
    }

    /** All packages that must stay allowed during a session (UI defaults + telecom internals). */
    fun alwaysAllowedPackages(context: Context): Set<String> {
        enforceCache?.let { return it }
        val result = LinkedHashSet<String>()
        result.addAll(defaultUiPackages(context))
        result.addAll(Constants.ALWAYS_ALLOWED)
        result.addAll(Constants.SESSION_SYSTEM_UI)
        result.addAll(inputMethodPackages(context))
        // Include any installed package that looks like the system share/docs UI.
        val pm = context.packageManager
        for (pkg in Constants.SESSION_SYSTEM_UI) {
            runCatching { pm.getApplicationInfo(pkg, 0); result.add(pkg) }
        }
        enforceCache = result
        return result
    }

    /**
     * Soft-keyboard packages that must not trigger the focus bounce when typing
     * inside a whitelisted app (Gboard, OEM IMEs, etc.).
     */
    fun inputMethodPackages(context: Context): Set<String> {
        imeCache?.let { return it }
        val result = LinkedHashSet<String>()
        result.addAll(Constants.SESSION_IME)
        runCatching {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { info ->
                result.add(info.packageName)
            }
        }
        runCatching {
            val defaultId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            defaultId?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        }
        imeCache = result
        return result
    }

    /** True for packages that host share sheets / docs / permission UI mid-flow. */
    fun isTransientSystemUi(pkg: String): Boolean {
        if (pkg in Constants.SESSION_SYSTEM_UI) return true
        val lower = pkg.lowercase()
        return lower.contains("intentresolver") ||
            lower.contains("documentsui") ||
            lower.contains("permissioncontroller") ||
            lower.endsWith(".packageinstaller")
    }

    /** True when [pkg] is an input method (soft keyboard). */
    fun isInputMethod(pkg: String, context: Context? = null): Boolean {
        if (pkg in Constants.SESSION_IME) return true
        val lower = pkg.lowercase()
        if (lower.contains("inputmethod") || lower.contains("keyboard") ||
            lower.contains("honeyboard") || lower.contains("swiftkey")
        ) {
            return true
        }
        if (context != null && pkg in inputMethodPackages(context)) return true
        return false
    }

    fun alwaysAllowedLaunchable(context: Context): List<String> =
        defaultUiPackages(context).toList()

    fun invalidate() {
        uiCache = null
        enforceCache = null
        imeCache = null
    }

    private val CALCULATOR_FALLBACKS = listOf(
        "com.google.android.calculator",
        "com.android.calculator2",
        "com.sec.android.app.popupcalculator",
        "com.miui.calculator",
        "com.coloros.calculator",
        "com.oneplus.calculator"
    )
}
