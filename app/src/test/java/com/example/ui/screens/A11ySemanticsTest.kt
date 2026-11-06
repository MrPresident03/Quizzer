package com.example.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.BrainBlitzTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class A11ySemanticsTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsSwitchRowIsOneToggleableSwitchLabelledByItsTitle() {
        var changedTo: Boolean? = null
        compose.setContent {
            BrainBlitzTheme {
                PreferenceToggle(
                    title = "Sound Effects",
                    description = "Play buzzer alerts",
                    icon = Icons.Default.VolumeUp,
                    checked = false,
                    onCheckedChange = { changedTo = it },
                    tag = "sound_toggle"
                )
            }
        }
        val row = compose.onNodeWithTag("sound_toggle")
        row.assertIsToggleable().assertIsOff()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        row.assertTextContains("Sound Effects")
        row.performClick()
        assertEquals(true, changedTo)
    }

    @Test
    fun usedLifelineIsDisabledAndSaysSo() {
        var clicks = 0
        compose.setContent {
            BrainBlitzTheme {
              Row {
                LifelineButton(
                    label = "Skip", count = 0, icon = Icons.Default.SkipNext,
                    isActive = true, onClick = { clicks++ }, tag = "used"
                )
                LifelineButton(
                    label = "Skip", count = 1, icon = Icons.Default.SkipNext,
                    isActive = true, onClick = { clicks += 10 }, tag = "fresh"
                )
              }
            }
        }
        compose.onNodeWithTag("used").assertIsNotEnabled().assertContentDescriptionEquals("Skip, used")
        compose.onNodeWithTag("fresh").assertIsEnabled().assertContentDescriptionEquals("Skip")
        compose.onNodeWithTag("used").performClick()
        assertEquals(0, clicks)
        compose.onNodeWithTag("fresh").performClick()
        assertEquals(10, clicks)
    }

    @Test
    fun timerBarAnnouncesSecondsLeft() {
        compose.setContent {
            BrainBlitzTheme { QuizTimerBar(timeLeft = { 12 }, timerSeconds = 30) }
        }
        compose.onNodeWithTag("quiz_timer_bar")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "12 seconds left"))
    }

    @Test
    fun hoursUseATwelveHourClock() {
        assertEquals("12 am", formatHour12(0))
        assertEquals("1 am", formatHour12(1))
        assertEquals("12 pm", formatHour12(12))
        assertEquals("6 pm", formatHour12(18))
        assertEquals("11 pm", formatHour12(23))
    }

    @Test
    fun avatarsAreReadAsWordsNotIds() {
        assertEquals("Avatar 1", avatarDescription("avatar_1"))
        assertEquals("Avatar 4", avatarDescription("avatar_4"))
    }
}
