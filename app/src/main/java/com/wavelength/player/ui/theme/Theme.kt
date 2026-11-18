package com.wavelength.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object WavelengthColors {
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Cyan = Color(0xFF22D3EE)
    val Ink = Color(0xFF0A0B14)
    val Night = Color(0xFF12131F)
    val Slate = Color(0xFF1C1E2E)
    val Muted = Color(0xFF9CA3AF)

    /** The logo gradient, used for the play button and accents. */
    val Brand = Brush.linearGradient(listOf(Violet, Pink, Cyan))
}

private val Scheme = darkColorScheme(
    primary = WavelengthColors.Violet,
    onPrimary = Color.White,
    secondary = WavelengthColors.Pink,
    onSecondary = Color.White,
    tertiary = WavelengthColors.Cyan,
    onTertiary = WavelengthColors.Ink,
    background = WavelengthColors.Ink,
    onBackground = Color(0xFFF3F4F6),
    surface = WavelengthColors.Ink,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = WavelengthColors.Slate,
    onSurfaceVariant = WavelengthColors.Muted,
    surfaceContainer = WavelengthColors.Night,
    surfaceContainerHigh = WavelengthColors.Slate,
    surfaceContainerHighest = Color(0xFF262838),
    surfaceContainerLow = WavelengthColors.Night,
    outline = Color(0xFF3A3D52),
    outlineVariant = Color(0xFF26283A),
)

@Composable
fun WavelengthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
