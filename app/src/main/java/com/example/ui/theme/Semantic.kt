package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A container colour with the content colour that reads on it. */
@Immutable
class ChipColors(val container: Color, val content: Color)

/**
 * Meaning-carrying colours that Material's scheme has no slot for. The text colours are tuned to
 * read on every theme's surface and surfaceVariant; [success] and [error] are strong fills that
 * take white icons. ThemeContrastTest checks all of it.
 */
@Immutable
class SemanticColors(
    val successText: Color,
    val errorText: Color,
    val xpText: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    val easy: ChipColors,
    val medium: ChipColors,
    val hard: ChipColors
) {
    fun difficulty(level: String): ChipColors = when (level.lowercase()) {
        "easy" -> easy
        "hard" -> hard
        else -> medium
    }
}

private val LightSemantic = SemanticColors(
    successText = Color(0xFF1B5E20),
    errorText = Color(0xFFA8201A),
    xpText = Color(0xFF7A4F00),
    success = CorrectGreen,
    error = WrongRed,
    warning = GoldYellow,
    easy = ChipColors(Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    medium = ChipColors(Color(0xFFFFE0A3), Color(0xFF5C3D00)),
    hard = ChipColors(Color(0xFFFFD9D6), Color(0xFF8C1D18))
)

private val DarkSemantic = SemanticColors(
    successText = Color(0xFFA5D6A7),
    errorText = Color(0xFFFFB4AB),
    xpText = Color(0xFFFFCA28),
    success = CorrectGreen,
    error = WrongRed,
    warning = GoldYellow,
    easy = ChipColors(Color(0xFF1B5E20), Color(0xFFC8E6C9)),
    medium = ChipColors(Color(0xFF5C3D00), Color(0xFFFFE0A3)),
    hard = ChipColors(Color(0xFF7A1C16), Color(0xFFFFDAD6))
)

internal fun semanticColors(darkTheme: Boolean): SemanticColors = if (darkTheme) DarkSemantic else LightSemantic

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

val MaterialTheme.semantic: SemanticColors
    @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
