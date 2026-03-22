package com.puzzle.jigsaw.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5B8A),
    onPrimary = Color(0xFFF9F7F1),
    primaryContainer = Color(0xFFD7E8FA),
    onPrimaryContainer = Color(0xFF0F2D4A),
    secondary = Color(0xFFC86A43),
    onSecondary = Color(0xFFFDF5EF),
    secondaryContainer = Color(0xFFF5DDCF),
    onSecondaryContainer = Color(0xFF4C210F),
    tertiary = Color(0xFF5A8C65),
    onTertiary = Color(0xFFF4FBF3),
    background = Color(0xFFF7F2E8),
    onBackground = Color(0xFF1F2429),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1F2429),
    surfaceVariant = Color(0xFFECE3D5),
    outline = Color(0xFF7A746C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3D0F2),
    onPrimary = Color(0xFF0F2D4A),
    primaryContainer = Color(0xFF1F466A),
    onPrimaryContainer = Color(0xFFD7E8FA),
    secondary = Color(0xFFF2B08D),
    onSecondary = Color(0xFF4C210F),
    secondaryContainer = Color(0xFF83482C),
    onSecondaryContainer = Color(0xFFF5DDCF),
    tertiary = Color(0xFFB8D7BE),
    onTertiary = Color(0xFF213B27),
    background = Color(0xFF121519),
    onBackground = Color(0xFFE8E0D5),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFE8E0D5),
    surfaceVariant = Color(0xFF343A41),
    outline = Color(0xFF92989E),
)

@Composable
fun PuzzleTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

