package com.hassan.zensposed.data.prefs

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores emergency-exit credentials: either a salted PBKDF2 password hash, or a
 * QR payload (app-generated or user-scanned). Only one method should be active
 * at a time (selected in settings).
 */
class SecurePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("zen_sposed_secure", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun hasQrSecret(): Boolean = !qrPayloadOrNull().isNullOrBlank()

    /** True when the registered exit QR came from the user's camera scan. */
    fun isCustomQr(): Boolean = prefs.getBoolean(KEY_QR_CUSTOM, false)

    fun setPassword(password: String) {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password.toCharArray(), salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun clearPassword() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
    }

    fun verify(password: String): Boolean {
        return try {
            val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
            val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            val actual = pbkdf2(password.toCharArray(), salt)
            constantTimeEquals(expected, actual)
        } catch (_: Throwable) {
            false
        }
    }

    /** Exact payload that must be scanned to exit (generated or custom). */
    fun qrPayloadOrNull(): String? {
        migrateLegacyQrIfNeeded()
        return prefs.getString(KEY_QR_PAYLOAD, null)?.takeIf { it.isNotBlank() }
    }

    /** Creates an app-generated QR payload if none exists yet. */
    fun ensureQrSecret(): String {
        migrateLegacyQrIfNeeded()
        qrPayloadOrNull()?.let { return it }
        return regenerateQrSecret()
    }

    fun regenerateQrSecret(): String {
        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val payload = "$QR_PREFIX$token"
        prefs.edit()
            .putString(KEY_QR_PAYLOAD, payload)
            .putBoolean(KEY_QR_CUSTOM, false)
            .remove(KEY_QR_SECRET)
            .apply()
        return payload
    }

    /** Register any scanned QR content as the exit key. */
    fun setCustomQrPayload(scanned: String): Boolean {
        val payload = scanned.trim()
        if (payload.isEmpty()) return false
        prefs.edit()
            .putString(KEY_QR_PAYLOAD, payload)
            .putBoolean(KEY_QR_CUSTOM, true)
            .remove(KEY_QR_SECRET)
            .apply()
        return true
    }

    fun clearQrSecret() {
        prefs.edit()
            .remove(KEY_QR_PAYLOAD)
            .remove(KEY_QR_CUSTOM)
            .remove(KEY_QR_SECRET)
            .apply()
    }

    fun verifyQrPayload(scanned: String): Boolean {
        val expected = qrPayloadOrNull() ?: return false
        return scanned.trim() == expected
    }

    private fun migrateLegacyQrIfNeeded() {
        if (prefs.contains(KEY_QR_PAYLOAD)) return
        val legacy = prefs.getString(KEY_QR_SECRET, null) ?: return
        if (legacy.isBlank()) return
        prefs.edit()
            .putString(KEY_QR_PAYLOAD, "$QR_PREFIX$legacy")
            .putBoolean(KEY_QR_CUSTOM, false)
            .remove(KEY_QR_SECRET)
            .apply()
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    companion object {
        const val QR_PREFIX = "ZENSPOSED_EXIT:"
        private const val KEY_SALT = "pw_salt"
        private const val KEY_HASH = "pw_hash"
        private const val KEY_QR_SECRET = "qr_secret" // legacy
        private const val KEY_QR_PAYLOAD = "qr_payload"
        private const val KEY_QR_CUSTOM = "qr_custom"
        private const val SALT_LEN = 16
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
    }
}
