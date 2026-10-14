package com.example.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.UserStats
import com.example.ui.theme.BrainBlitzTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsScreenUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun themesAreFreeToPickAndProfileSavesOnlyWhenChanged() {
        var selectedTheme: String? = null
        var saved: Pair<String, String>? = null
        compose.setContent {
            BrainBlitzTheme {
                SettingsScreen(
                    userStats = UserStats(displayName = "Quiz Champ", avatarId = "avatar_1"),
                    onUpdateToggles = { _, _, _ -> },
                    onResetProgress = {},
                    onClearQuestionHistory = {},
                    onUpdateProfile = { name, avatar -> saved = name to avatar },
                    onSelectTheme = { selectedTheme = it }
                )
            }
        }

        compose.onNodeWithTag("select_theme_forest_green").performClick()
        assertEquals("forest_green", selectedTheme)

        compose.onNodeWithTag("save_profile_button").assertIsNotEnabled()
        compose.onNodeWithTag("profile_nickname_input").performTextReplacement("Durbs")
        compose.onNodeWithTag("avatar_avatar_3").performClick()
        compose.onNodeWithTag("save_profile_button").assertIsEnabled().performClick()
        assertEquals("Durbs" to "avatar_3", saved)
    }
}
