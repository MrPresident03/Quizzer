package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// --- COLOR SCHEME DEFINITIONS ---

// Midnight Blue / Sleek Interface
private val MidnightDark = darkColorScheme(
    primary = Color(0xFF818CF8), // Indigo 400
    primaryContainer = Color(0xFF312E81), // Indigo 900
    secondary = Color(0xFFA78BFA), // Purple 400
    background = MidnightBackgroundDark,
    surface = MidnightSurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)
private val MidnightLight = lightColorScheme(
    primary = MidnightPrimary, // Indigo 600
    primaryContainer = MidnightPrimaryContainer,
    secondary = MidnightSecondary, // Purple 500
    background = MidnightBackgroundLight, // Slate light #F3F4F9
    surface = MidnightSurfaceLight, // White cards
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

// Forest Green
private val ForestDark = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    primaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF81C784),
    background = ForestBackgroundDark,
    surface = ForestSurfaceDark,
    onPrimary = Color(0xFF081C10),
    onSecondary = Color(0xFF081C10),
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9)
)
private val ForestLight = lightColorScheme(
    primary = ForestPrimary,
    primaryContainer = ForestPrimaryContainer,
    secondary = ForestSecondary,
    background = ForestBackgroundLight,
    surface = ForestSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF081C10),
    onSurface = Color(0xFF081C10)
)

// Sunset Orange
private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB74D),
    primaryContainer = Color(0xFFE65100),
    secondary = Color(0xFFFF9800),
    background = SunsetBackgroundDark,
    surface = SunsetSurfaceDark,
    onPrimary = Color(0xFF211005),
    onSecondary = Color(0xFF211005),
    onBackground = Color(0xFFFFEDE5),
    onSurface = Color(0xFFFFEDE5)
)
private val SunsetLight = lightColorScheme(
    primary = SunsetPrimary,
    primaryContainer = SunsetPrimaryContainer,
    secondary = SunsetSecondary,
    background = SunsetBackgroundLight,
    surface = SunsetSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF211005),
    onSurface = Color(0xFF211005)
)

// Classic Purple
private val ClassicDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    primaryContainer = Color(0xFF4F378B),
    secondary = Color(0xFFCCC2DC),
    background = ClassicBackgroundDark,
    surface = ClassicSurfaceDark,
    onPrimary = Color(0xFF21005D),
    onSecondary = Color(0xFF21005D),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
)
private val ClassicLight = lightColorScheme(
    primary = ClassicPrimary,
    primaryContainer = ClassicPrimaryContainer,
    secondary = ClassicSecondary,
    background = ClassicBackgroundLight,
    surface = ClassicSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun BrainBlitzTheme(
    themeId: String = "midnight_blue", // "midnight_blue", "forest_green", "sunset_orange", "classic"
    darkTheme: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeId) {
        "forest_green" -> if (darkTheme) ForestDark else ForestLight
        "sunset_orange" -> if (darkTheme) SunsetDark else SunsetLight
        "classic" -> if (darkTheme) ClassicDark else ClassicLight
        else -> if (darkTheme) MidnightDark else MidnightLight // default "midnight_blue"
    }

    // "Increase Font Text Size" setting: scale every sp-based text size (including hard-coded
    // fontSize values) on top of the system font scale.
    val density = LocalDensity.current
    val scaledDensity = if (largeText) Density(density.density, density.fontScale * LARGE_TEXT_SCALE) else density

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private const val LARGE_TEXT_SCALE = 1.15f
