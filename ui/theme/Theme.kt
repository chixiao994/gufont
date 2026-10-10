package com.gufont.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFD9A96A),
    onPrimary = Color(0xFF1A1A22),
    primaryContainer = Color(0xFF3A2E1E),
    onPrimaryContainer = Color(0xFFF5E6C8),
    background = Color(0xFF0E0E14),
    onBackground = Color(0xFFE8E4DA),
    surface = Color(0xFF1A1A22),
    onSurface = Color(0xFFE8E4DA),
    surfaceVariant = Color(0xFF26262E),
    onSurfaceVariant = Color(0xFFBFBAAE),
    outline = Color(0xFF5A5A66),
    error = Color(0xFFE57373)
)

@Composable
fun GuFontTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
