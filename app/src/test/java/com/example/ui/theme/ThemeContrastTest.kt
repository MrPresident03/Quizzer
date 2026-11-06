package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x contrast for every theme in light and dark: body text needs at least 4.5:1. */
class ThemeContrastTest {

    private val minimum = 4.5

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private val failures = mutableListOf<String>()

    private fun check(where: String, pair: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        if (r < minimum) failures += "$where $pair = ${"%.2f".format(r)}:1"
    }

    @Test
    fun everyThemeAndModeKeepsItsColourPairsReadable() {
        for (themeId in ThemeIds) for (dark in listOf(false, true)) {
            val where = "$themeId/${if (dark) "dark" else "light"}"
            val c = brainBlitzColorScheme(themeId, dark)
            val sem = semanticColors(dark)

            check(where, "primary/onPrimary", c.onPrimary, c.primary)
            check(where, "primaryContainer/onPrimaryContainer", c.onPrimaryContainer, c.primaryContainer)
            check(where, "secondary/onSecondary", c.onSecondary, c.secondary)
            check(where, "secondaryContainer/onSecondaryContainer", c.onSecondaryContainer, c.secondaryContainer)
            check(where, "surface/onSurface", c.onSurface, c.surface)
            check(where, "surfaceVariant/onSurfaceVariant", c.onSurfaceVariant, c.surfaceVariant)
            check(where, "background/onBackground", c.onBackground, c.background)
            // Secondary text on the page itself, and accent text on the surfaces it is used on.
            check(where, "background/onSurfaceVariant", c.onSurfaceVariant, c.background)
            check(where, "surface/primary", c.primary, c.surface)
            check(where, "background/primary", c.primary, c.background)
            check(where, "cardContainer/onSurface", c.onSurface, c.surfaceContainerHighest)

            for (bg in listOf("surface" to c.surface, "surfaceVariant" to c.surfaceVariant, "background" to c.background)) {
                check(where, "successText on ${bg.first}", sem.successText, bg.second)
                check(where, "errorText on ${bg.first}", sem.errorText, bg.second)
                check(where, "xpText on ${bg.first}", sem.xpText, bg.second)
            }
            for ((name, chip) in listOf("easy" to sem.easy, "medium" to sem.medium, "hard" to sem.hard)) {
                check(where, "$name chip content/container", chip.content, chip.container)
            }
            // White icons sit on the green and red answer fills.
            check(where, "white on success fill", Color.White, sem.success)
            check(where, "white on error fill", Color.White, sem.error)
        }
        assertTrue("Low contrast:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun difficultyLevelsMapToTheirChips() {
        val sem = semanticColors(false)
        assertTrue(sem.difficulty("easy") === sem.easy)
        assertTrue(sem.difficulty("Hard") === sem.hard)
        assertTrue(sem.difficulty("medium") === sem.medium)
        assertTrue(sem.difficulty("anything else") === sem.medium)
    }
}
