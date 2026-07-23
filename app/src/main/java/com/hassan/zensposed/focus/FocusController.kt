package com.hassan.zensposed.focus

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.hassan.zensposed.core.Constants
import com.hassan.zensposed.core.PrivilegeRequirements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-process observable session state, driven by FocusService and read by Compose UI. */
object FocusController {

    private const val TAG = "ZenSposed/Focus"

    data class UiState(
        val active: Boolean = false,
        val remainingMs: Long = 0L,
        val elapsedMs: Long = 0L,
        val plannedMs: Long = 0L,
        val spaceName: String = "Focus",
        val themeId: String = "ocean",
        val noLimit: Boolean = false,
        val whitelist: Set<String> = emptySet()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun publish(state: UiState) {
        _state.value = state
    }

    /**
     * @return false if root / LSPosed requirements are not met (session not started).
     */
    fun startFocus(
        context: Context,
        durationMinutes: Int,
        noLimit: Boolean,
        spaceName: String,
        themeId: String,
        whitelist: Set<String> = emptySet()
    ): Boolean {
        val status = PrivilegeRequirements.check()
        if (!status.ready) {
            Log.w(TAG, "Refusing startFocus: ${status.missingMessage()}")
            Toast.makeText(context, status.missingMessage(), Toast.LENGTH_LONG).show()
            return false
        }
        val intent = Intent(context, FocusService::class.java).apply {
            action = Constants.ACTION_START_FOCUS
            putExtra(Constants.EXTRA_DURATION_MINUTES, durationMinutes)
            putExtra("no_limit", noLimit)
            putExtra(Constants.EXTRA_SPACE_NAME, spaceName)
            putExtra(Constants.EXTRA_THEME_ID, themeId)
            putExtra(Constants.EXTRA_WHITELIST, whitelist.joinToString(","))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        return true
    }

    fun stopFocus(context: Context) {
        val intent = Intent(context, FocusService::class.java).apply {
            action = Constants.ACTION_STOP_FOCUS
        }
        context.startService(intent)
    }
}
