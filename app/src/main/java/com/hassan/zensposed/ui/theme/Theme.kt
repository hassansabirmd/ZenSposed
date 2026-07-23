package com.hassan.zensposed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Color0E = androidx.compose.ui.graphics.Color(0xFF0E1116)
private val Color16 = androidx.compose.ui.graphics.Color(0xFF161A22)

private val LightColors = lightColorScheme(
    primary = ZenBlue,
    onPrimary = CardWhite,
    primaryContainer = ZenBlueLight,
    secondary = ZenAccent,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary
)

private val DarkColors = darkColorScheme(
    primary = ZenBlueLight,
    onPrimary = CardWhite,
    primaryContainer = ZenBlueDark,
    secondary = ZenAccent,
    background = Color0E,
    onBackground = CardWhite,
    surface = Color16,
    onSurface = CardWhite
)

val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun ZenSposedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
