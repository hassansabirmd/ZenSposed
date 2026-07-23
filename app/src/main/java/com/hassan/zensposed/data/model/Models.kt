package com.hassan.zensposed.data.model

import androidx.compose.ui.graphics.Color

/** A saved focus preset shown on the home screen ("Deep Zen", "Work", "Life"...). */
data class FocusSpace(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val noLimit: Boolean = false
)

/** A user-defined profile: custom timer + its own set of whitelisted apps. */
data class Profile(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val noLimit: Boolean = false,
    val whitelist: Set<String> = emptySet()
)

/** Nature background choices for the focus screen. */
enum class NatureTheme(val id: String, val displayName: String, val gradient: List<Color>) {
    OCEAN("ocean", "Ocean", listOf(Color(0xFF1B2FE0), Color(0xFF2536F5), Color(0xFF4A5CFF))),
    FOREST("forest", "Forest", listOf(Color(0xFF0B3D2E), Color(0xFF16624A), Color(0xFF2E8B6B))),
    SUNSET("sunset", "Sunset", listOf(Color(0xFF6A1B4D), Color(0xFFB83280), Color(0xFFED8936))),
    NIGHT("night", "Night Sky", listOf(Color(0xFF05060F), Color(0xFF11162E), Color(0xFF232B54))),
    SAND("sand", "Desert", listOf(Color(0xFF7B5E2A), Color(0xFFB08946), Color(0xFFE0B973))),
    OLED("oled", "OLED Black", listOf(Color.Black, Color.Black, Color.Black));

    companion object {
        fun fromId(id: String?): NatureTheme = entries.firstOrNull { it.id == id } ?: OCEAN
    }
}

/** A whitelisted app the user can still open during a focus session. */
data class WhitelistedApp(
    val packageName: String,
    val label: String
)
