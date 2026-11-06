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
// Every on-colour is chosen so its pair reads at 4.5:1 or better; ThemeContrastTest enforces it.

// Midnight Blue / Sleek Interface
internal val MidnightDark = darkColorScheme(
    primary = Color(0xFF818CF8), onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81), onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA78BFA), onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF4C1D95), onSecondaryContainer = Color(0xFFEDE9FE),
    background = MidnightBackgroundDark, onBackground = Color(0xFFF8FAFC),
    surface = MidnightSurfaceDark, onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155), onSurfaceVariant = Color(0xFFCBD5E1)
)
internal val MidnightLight = lightColorScheme(
    primary = MidnightPrimary, onPrimary = Color.White,
    primaryContainer = MidnightPrimaryContainer, onPrimaryContainer = Color(0xFF312E81),
    secondary = MidnightSecondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE), onSecondaryContainer = Color(0xFF4C1D95),
    background = MidnightBackgroundLight, onBackground = Color(0xFF0F172A),
    surface = MidnightSurfaceLight, onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0), onSurfaceVariant = Color(0xFF334155)
)

// Forest Green
internal val ForestDark = darkColorScheme(
    primary = Color(0xFFA5D6A7), onPrimary = Color(0xFF081C10),
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF81C784), onSecondary = Color(0xFF081C10),
    secondaryContainer = Color(0xFF2E5A31), onSecondaryContainer = Color(0xFFDCEDC8),
    background = ForestBackgroundDark, onBackground = Color(0xFFE8F5E9),
    surface = ForestSurfaceDark, onSurface = Color(0xFFE8F5E9),
    surfaceVariant = Color(0xFF1F4A33), onSurfaceVariant = Color(0xFFC8E6C9)
)
internal val ForestLight = lightColorScheme(
    primary = ForestPrimary, onPrimary = Color.White,
    primaryContainer = ForestPrimaryContainer, onPrimaryContainer = Color(0xFF0A2E0D),
    secondary = ForestSecondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDC8), onSecondaryContainer = Color(0xFF1B3A1D),
    background = ForestBackgroundLight, onBackground = Color(0xFF081C10),
    surface = ForestSurfaceLight, onSurface = Color(0xFF081C10),
    surfaceVariant = Color(0xFFD0E6D2), onSurfaceVariant = Color(0xFF2E4A33)
)

// Sunset Orange
internal val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = Color(0xFF211005),
    primaryContainer = Color(0xFF9A3A00), onPrimaryContainer = Color(0xFFFFE0CC),
    secondary = Color(0xFFFF9800), onSecondary = Color(0xFF211005),
    secondaryContainer = Color(0xFF6B3A0F), onSecondaryContainer = Color(0xFFFFDCC2),
    background = SunsetBackgroundDark, onBackground = Color(0xFFFFEDE5),
    surface = SunsetSurfaceDark, onSurface = Color(0xFFFFEDE5),
    surfaceVariant = Color(0xFF4A2B18), onSurfaceVariant = Color(0xFFF0CDB5)
)
internal val SunsetLight = lightColorScheme(
    primary = SunsetPrimary, onPrimary = Color.White,
    primaryContainer = SunsetPrimaryContainer, onPrimaryContainer = Color(0xFF3E1A00),
    secondary = SunsetSecondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF4A2500),
    background = SunsetBackgroundLight, onBackground = Color(0xFF211005),
    surface = SunsetSurfaceLight, onSurface = Color(0xFF211005),
    surfaceVariant = Color(0xFFF5D5C0), onSurfaceVariant = Color(0xFF4A2B18)
)

// Classic Purple
internal val ClassicDark = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    background = ClassicBackgroundDark, onBackground = Color(0xFFE6E1E5),
    surface = ClassicSurfaceDark, onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0)
)
internal val ClassicLight = lightColorScheme(
    primary = ClassicPrimary, onPrimary = Color.White,
    primaryContainer = ClassicPrimaryContainer, onPrimaryContainer = Color(0xFF21005D),
    secondary = ClassicSecondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    background = ClassicBackgroundLight, onBackground = Color(0xFF1C1B1F),
    surface = ClassicSurfaceLight, onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F)
)

/** The theme ids the app knows, in the order they are offered. */
internal val ThemeIds = listOf("midnight_blue", "forest_green", "sunset_orange", "classic")

internal fun brainBlitzColorScheme(themeId: String, darkTheme: Boolean): ColorScheme {
    val scheme = when (themeId) {
        "forest_green" -> if (darkTheme) ForestDark else ForestLight
        "sunset_orange" -> if (darkTheme) SunsetDark else SunsetLight
        "classic" -> if (darkTheme) ClassicDark else ClassicLight
        else -> if (darkTheme) MidnightDark else MidnightLight // default "midnight_blue"
    }
    // Cards and dialogs use the surfaceContainer roles, which otherwise fall back to the purple
    // baseline in every theme. Tie them to the theme's own surface so the contrast pairs hold.
    return scheme.copy(
        surfaceContainerLowest = scheme.surface,
        surfaceContainerLow = scheme.surface,
        surfaceContainer = scheme.surface,
        surfaceContainerHigh = scheme.surface,
        surfaceContainerHighest = scheme.surface
    )
}

@Composable
fun BrainBlitzTheme(
    themeId: String = "midnight_blue", // "midnight_blue", "forest_green", "sunset_orange", "classic"
    darkTheme: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = brainBlitzColorScheme(themeId, darkTheme)

    // "Increase Font Text Size" setting: scale every sp-based text size (including hard-coded
    // fontSize values) on top of the system font scale.
    val density = LocalDensity.current
    val scaledDensity = if (largeText) Density(density.density, density.fontScale * LARGE_TEXT_SCALE) else density

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalSemanticColors provides semanticColors(darkTheme)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private const val LARGE_TEXT_SCALE = 1.15f
