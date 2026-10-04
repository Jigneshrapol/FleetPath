package com.fleetpath.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Palette from the FleetPath UI designs. */
object FP {
    val Ink = Color(0xFF0E1A2B)
    val Ink2 = Color(0xFF26334A)
    val Blue = Color(0xFF1A56DB)
    val BlueSoft = Color(0xFFE8EFFE)
    val BlueLight = Color(0xFFA9C1F5)
    val Orange = Color(0xFFC2410C)
    val OrangeSoft = Color(0xFFFDEDE3)
    val OrangeText = Color(0xFF9A3412)
    val OrangeMid = Color(0xFFE58B4E)
    val Green = Color(0xFF0B6B4B)
    val GreenDot = Color(0xFF12915F)
    val GreenSoft = Color(0xFFE3F4EC)
    val Amber = Color(0xFF7A5A00)
    val AmberSoft = Color(0xFFFDF3D7)
    val Muted = Color(0xFF4A5668)
    val Subtle = Color(0xFF5B6676)
    val Line = Color(0xFFE1E6EC)
    val Field = Color(0xFFC9D1DB)
    val Surface2 = Color(0xFFF3F5F8)
    val Track = Color(0xFFEEF1F4)
    val MapBg = Color(0xFFEAEEF2)
    val Grey = Color(0xFFD3D9E0)
}

val Mono: FontFamily = FontFamily.Monospace

@Composable
fun FleetPathTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FP.Blue,
            onPrimary = Color.White,
            primaryContainer = FP.BlueSoft,
            onPrimaryContainer = FP.Blue,
            secondary = FP.Ink,
            onSecondary = Color.White,
            background = Color.White,
            onBackground = FP.Ink,
            surface = Color.White,
            onSurface = FP.Ink,
            surfaceVariant = FP.Surface2,
            onSurfaceVariant = FP.Muted,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = FP.Surface2,
            outline = FP.Field,
            outlineVariant = FP.Line,
            error = FP.Orange,
        ),
        typography = Typography(),
        content = content,
    )
}
