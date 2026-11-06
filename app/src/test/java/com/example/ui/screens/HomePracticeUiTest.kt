package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import com.example.data.model.UserStats
import com.example.data.repository.DailyResult
import com.example.ui.theme.BrainBlitzTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class HomePracticeUiTest {

    @get:Rule val compose = createComposeRule()

    private data class QuizArgs(val cats: List<String>, val diff: String, val count: Int, val format: String, val timed: Boolean)
    private data class FlagArgs(val mode: String, val diff: String, val count: Int)

    private var quiz: QuizArgs? = null
    private var flag: FlagArgs? = null

    private fun practice() {
        compose.setContent {
            BrainBlitzTheme {
                TopicSelectionScreen(
                    onStartQuiz = { c, d, n, f, t -> quiz = QuizArgs(c, d, n, f, t) },
                    onStartFlagQuiz = { m, d, n -> flag = FlagArgs(m, d, n) },
                    todayTopic = "History"
                )
            }
        }
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun practiceStartsWithSelectedTopicsCountAndFormat() {
        practice()
        tap("topic_History")
        tap("topic_Geography")
        tap("opt_count_20")
        tap("opt_format_True/False")
        tap("start_practice_quiz_button")
        assertEquals(QuizArgs(listOf("History", "Geography"), "Mixed", 20, "True/False", true), quiz)
        assertNull(flag)
    }

    @Test
    fun startButtonLabelReflectsSelection() {
        practice()
        compose.onNodeWithText("Start · All topics · 10 questions").assertExists()
        tap("topic_History")
        tap("topic_Geography")
        tap("topic_Entertainment")
        tap("opt_count_999")
        compose.onNodeWithText("Start · 3 topics · Endless").assertExists()
    }

    @Test
    fun flagsAreExclusiveAndStartTheFlagQuiz() {
        practice()
        tap("topic_History")
        tap("special_flags")
        compose.onNodeWithText("Start · Flags · 10 questions").assertExists()
        tap("opt_format_flag_to_country")
        tap("opt_difficulty_Hard")
        tap("opt_count_20")
        compose.onNodeWithTag("opt_count_999").assertDoesNotExist()
        tap("start_practice_quiz_button")
        assertEquals(FlagArgs("flag_to_country", "Hard", 20), flag)
        assertNull(quiz)

        // Picking a topic drops Flags and goes back to the normal quiz.
        tap("topic_Geography")
        compose.onNodeWithText("Start · Geography · 10 questions").assertExists()
        tap("start_practice_quiz_button")
        assertEquals(listOf("Geography"), quiz?.cats)
    }

    @Test
    fun capitalCitiesAloneHidesTrueFalse() {
        practice()
        compose.onNodeWithText("True/False").assertExists()
        tap("topic_Capital Cities Challenge")
        compose.onNodeWithText("True/False").assertDoesNotExist()
        tap("topic_History")
        compose.onNodeWithText("True/False").assertExists()
    }

    private fun home(
        mistakes: Int = 0,
        played: Boolean = false,
        result: DailyResult? = null,
        stats: UserStats = UserStats(),
        today: LocalDate = LocalDate.of(2026, 11, 6)
    ) {
        compose.setContent {
            BrainBlitzTheme {
                DashboardScreen(
                    userStats = stats,
                    dailyPlayed = played,
                    onNavigateToDailyChallenge = {},
                    onNavigateToPracticeSelection = {},
                    onNavigateToProfile = {},
                    mistakeCount = mistakes,
                    todayResult = result,
                    today = today
                )
            }
        }
    }

    @Test
    fun mistakesRowOnlyWhenThereAreMistakes() {
        home(mistakes = 0)
        compose.onNodeWithTag("review_mistakes_card").assertDoesNotExist()
    }

    @Test
    fun mistakesRowShowsCount() {
        home(mistakes = 12)
        compose.onNodeWithText("Review your mistakes (12)").assertIsDisplayed()
    }

    @Test
    fun dailyCardOffersPlayWhenNotPlayed() {
        home(played = false)
        compose.onNodeWithText("Play today's challenge").assertIsDisplayed()
        compose.onNodeWithTag("daily_share_button").assertDoesNotExist()
    }

    @Test
    fun dailyCardShowsScoreAndShareWhenPlayed() {
        val result = DailyResult("2026-11-06", List(10) { it < 7 }, 95)
        home(played = true, result = result)
        compose.onNodeWithText("Done · 7/10").assertIsDisplayed()
        compose.onNodeWithTag("daily_share_button").assertIsDisplayed()
        compose.onNodeWithText("Play today's challenge").assertDoesNotExist()
    }

    @Test
    fun streakChipUsesCorrectPlural() {
        assertEquals("1 day", streakLabel(1))
        assertEquals("2 days", streakLabel(2))
        assertEquals("0 days", streakLabel(0))
        home(stats = UserStats(currentStreak = 1, lastQuizDateStr = "2026-11-06"))
        compose.onNodeWithText("🔥 1 day").assertExists()
    }

    @Test
    fun topicOfTheDayKeepsTheWeekdayMapping() {
        fun on(day: Int) = topicOfTheDay(LocalDate.of(2026, 11, day))
        assertEquals("History", on(7)) // Saturday
        assertEquals("Science & Nature", on(8)) // Sunday
        assertEquals("Geography", on(9)) // Monday
        assertEquals("Sports & Recreation", on(10)) // Tuesday
        assertEquals("Entertainment", on(11)) // Wednesday
        assertEquals("Technology & Computing", on(12)) // Thursday
        assertEquals("General Knowledge", on(6)) // Friday
    }
}
