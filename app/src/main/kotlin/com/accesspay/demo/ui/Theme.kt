package com.accesspay.demo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// AccessPay host & high-contrast colours matching the AccessAI UI spec (1b / 1c / 1f / 1i).
private val Light = lightColorScheme(
    primary = Color(0xFF1B6B2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF3EF),
    onPrimaryContainer = Color(0xFF0B1F14),
    surface = Color.White,
    onSurface = Color(0xFF10201A),
    onSurfaceVariant = Color(0xFF3D4A42),
    background = Color(0xFFF2F4F3),
    onBackground = Color(0xFF10201A),
    outline = Color(0xFF6B7A71),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6FE07A),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF121212),
    onPrimaryContainer = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE8E8E8),
    background = Color.Black,
    onBackground = Color.White,
    outline = Color.White,
    error = Color(0xFFFF9A91),
)

/** Status colours; always paired with a word, never used alone. */
object StatusColors {
    @Composable fun positive(): Color =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF7CF08C) else Color(0xFF146B34)

    @Composable fun warning(): Color =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFFC46B) else Color(0xFF8A4B00)
}

@Composable
fun AccessPayTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
