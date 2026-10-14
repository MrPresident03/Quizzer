package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Question
import com.example.data.model.UserStats
import com.example.data.repository.DailyResult
import com.example.ui.theme.BrainBlitzTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi") // phone-sized screen so everything is on screen
class QuizScreensUiTest {

    @get:Rule val compose = createComposeRule()

    private val question = Question(
        text = "Who painted the Mona Lisa?",
        optionsList = listOf("Van Gogh", "Leonardo da Vinci", "Picasso", "Monet"),
        correctIndex = 1,
        category = "Art & Architecture",
        difficulty = "easy",
        explanation = "Leonardo painted it in the early 1500s."
    )

    // Sound off so the tone generator isn't touched in tests.
    private val stats = UserStats(isSoundEffectsEnabled = false, isHapticFeedbackEnabled = false)

    private fun playScreen(
        questions: List<Question> = listOf(question),
        selected: Int? = null,
        revealed: Boolean = false,
        isLoading: Boolean = false,
        onSelect: (Int) -> Unit = {},
        onNext: () -> Unit = {}
    ) {
        compose.setContent {
            BrainBlitzTheme {
                QuizPlayScreen(
                    questions = questions,
                    currentIndex = 0,
                    selectedOption = selected,
                    isRevealed = revealed,
                    timeLeft = 60,
                    hiddenOptions = emptySet(),
                    audiencePoll = null,
                    userStats = stats,
                    onSelectOption = onSelect,
                    onUse5050 = {},
                    onUseSkip = {},
                    onUseExtraTime = {},
                    onUseAskAudience = {},
                    onQuitQuiz = {},
                    isLoading = isLoading,
                    onNext = onNext
                )
            }
        }
    }

    @Test
    fun beforeAnsweringShowsLifelinesAndNoExplanation() {
        var picked: Int? = null
        playScreen(onSelect = { picked = it })
        compose.onNodeWithTag("skip_lifeline").assertExists()
        compose.onNodeWithTag("answer_explanation").assertDoesNotExist()
        compose.onNodeWithTag("next_question_button").assertDoesNotExist()
        compose.onNodeWithTag("quiz_option_b").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun afterAWrongAnswerShowsTheCorrectAnswerExplanationAndNext() {
        var nextClicks = 0
        playScreen(selected = 0, revealed = true, onNext = { nextClicks++ })
        compose.onNodeWithTag("answer_explanation").assertExists()
        compose.onNodeWithText("Leonardo painted it in the early 1500s.").assertExists()
        compose.onNodeWithText("✗ Not quite — the answer is Leonardo da Vinci").assertExists()
        compose.onNodeWithText("See results").assertExists() // last question
        compose.onNodeWithTag("next_question_button").performClick()
        assertEquals(1, nextClicks)
    }

    @Test
    fun whileLoadingShowsAProgressMessage() {
        playScreen(questions = emptyList(), isLoading = true)
        compose.onNodeWithText("Finding fresh questions…").assertIsDisplayed()
    }

    @Test
    fun dailyLobbyShowsScoreGridAndShareOncePlayed() {
        var shared = 0
        compose.setContent {
            BrainBlitzTheme {
                DailyChallengeLobbyScreen(
                    userStats = stats,
                    dailyPlayed = true,
                    challengeNumber = 287,
                    todayResult = DailyResult("2026-10-14", listOf(true, false, true), 75),
                    onNavigateBack = {},
                    onStartDailyQuiz = {},
                    onShare = { shared++ }
                )
            }
        }
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("🟩🟥🟩").assertExists()
        compose.onNodeWithTag("start_daily_challenge_button").assertDoesNotExist()
        compose.onNodeWithTag("share_daily_button").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun resultsOnlyOfferSharingWhenGivenAShareAction() {
        compose.setContent {
            BrainBlitzTheme {
                QuizResultsScreen(
                    questions = listOf(question),
                    answers = listOf(true),
                    xpEarned = 60,
                    isDaily = false,
                    onReviewClick = {},
                    onDashboardFinish = {}
                )
            }
        }
        compose.onNodeWithText("+60 XP").assertExists()
        compose.onNodeWithTag("share_result_button").assertDoesNotExist()
    }
}
