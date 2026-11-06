package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserStats
import com.example.data.repository.QuizPrefs
import com.example.ui.screens.ResultTier
import com.example.ui.screens.resultTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupAndRoutingTest {

    private lateinit var context: Context
    private lateinit var prefs: QuizPrefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = QuizPrefs(context)
    }

    // --- onboarding flag ---

    @Test
    fun freshInstallShowsOnboardingAndStaysUndecided() {
        assertFalse(Startup.resolveOnboardingDone(prefs, null))
        assertFalse(Startup.resolveOnboardingDone(prefs, UserStats()))
        assertFalse(prefs.onboardingDecided)
        assertEquals(Routes.ONBOARDING, Startup.startRoute(false))
    }

    @Test
    fun existingPlayersAreMigratedAsDone() {
        val players = listOf(
            UserStats(totalQuestionsAnswered = 3),
            UserStats(xpPoints = 40),
            UserStats(displayName = "Alex")
        )
        for (stats in players) {
            context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            val fresh = QuizPrefs(context)
            assertTrue("$stats", Startup.resolveOnboardingDone(fresh, stats))
            assertTrue(fresh.onboardingDecided)
            assertTrue(fresh.onboardingDone)
            // The stored flag now answers on its own, even if stats are unavailable.
            assertTrue(Startup.resolveOnboardingDone(fresh, null))
        }
    }

    @Test
    fun answeredTopicCountersAloneMarkAnExistingPlayer() {
        prefs.addCategoryAnswered(mapOf("History" to 2))
        assertTrue(Startup.resolveOnboardingDone(prefs, UserStats()))
        assertTrue(prefs.onboardingDone)
    }

    @Test
    fun storedDecisionWinsOverStats() {
        // Reset-progress leaves "decided, not done": onboarding must show even though old stats linger briefly.
        prefs.onboardingDone = false
        assertFalse(Startup.resolveOnboardingDone(prefs, UserStats(xpPoints = 500)))
        prefs.onboardingDone = true
        assertTrue(Startup.resolveOnboardingDone(prefs, UserStats()))
    }

    @Test
    fun finishingOnboardingPersists() {
        prefs.onboardingDone = true
        assertTrue(QuizPrefs(context).onboardingDone)
        assertEquals(Routes.DASHBOARD, Startup.startRoute(true))
    }

    @Test
    fun startRouteResolvesFromStatsAndTimesOutToDashboard() = runTest {
        assertEquals(Routes.ONBOARDING, Startup.resolveStartRoute(prefs) { null })
        assertEquals(Routes.DASHBOARD, Startup.resolveStartRoute(prefs) { UserStats(xpPoints = 10) })
        assertTrue(prefs.onboardingDone)

        context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val slow = QuizPrefs(context)
        assertEquals(Routes.DASHBOARD, Startup.resolveStartRoute(slow) { delay(60_000); null })
        assertFalse("a timeout must not store a decision", slow.onboardingDecided)
    }

    // --- redirect conditions (B5) ---

    @Test
    fun quizRedirectsOnlyWhenNothingToShowAndNothingComing() {
        assertTrue(shouldRedirectFromQuiz(hasQuestions = false, isLoading = false, hasError = false, finished = false))
        assertFalse(shouldRedirectFromQuiz(hasQuestions = false, isLoading = true, hasError = false, finished = false))
        assertFalse(shouldRedirectFromQuiz(hasQuestions = false, isLoading = false, hasError = true, finished = false))
        assertFalse(shouldRedirectFromQuiz(hasQuestions = true, isLoading = false, hasError = false, finished = false))
        assertFalse(shouldRedirectFromQuiz(hasQuestions = false, isLoading = false, hasError = false, finished = true))
    }

    @Test
    fun resultsAndReviewNeedAFinishedSessionWithQuestions() {
        assertTrue(shouldRedirectFromResults(finished = false, hasQuestions = true))
        assertTrue(shouldRedirectFromResults(finished = true, hasQuestions = false))
        assertFalse(shouldRedirectFromResults(finished = true, hasQuestions = true))
    }

    // --- results tiers (B6) ---

    @Test
    fun resultTierNeverCelebratesAZeroScore() {
        assertEquals(ResultTier.PRACTISE, resultTier(0, 10))
        assertEquals(ResultTier.PRACTISE, resultTier(0, 0))
        assertEquals(ResultTier.PRACTISE, resultTier(4, 10))
        assertEquals(ResultTier.GOOD, resultTier(5, 10))
        assertEquals(ResultTier.GOOD, resultTier(7, 10))
        assertEquals(ResultTier.GREAT, resultTier(8, 10))
        assertEquals(ResultTier.GREAT, resultTier(9, 10))
        assertEquals(ResultTier.PERFECT, resultTier(10, 10))
    }
}
