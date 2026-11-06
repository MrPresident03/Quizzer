package com.example.ui

import androidx.navigation.NavController
import com.example.data.model.UserStats
import com.example.data.repository.QuizPrefs
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Route names shared by the nav host, MainActivity and tests. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val ONBOARDING = "onboarding"
}

/** Decides where the app starts, and migrates players from before the onboarding flag existed. */
object Startup {
    const val DEFAULT_DISPLAY_NAME = "Quiz Champ"
    private const val STATS_TIMEOUT_MS = 5_000L

    /** Anyone who has already played (or renamed themselves) must not be shown onboarding again. */
    fun isExistingPlayer(stats: UserStats?, answeredTopics: Set<String>): Boolean =
        answeredTopics.isNotEmpty() ||
            (stats != null &&
                (stats.totalQuestionsAnswered > 0 ||
                    stats.xpPoints > 0 ||
                    stats.displayName != DEFAULT_DISPLAY_NAME))

    /**
     * Whether onboarding is already done. A stored decision wins; otherwise existing players are
     * migrated (and the flag stored), and a fresh install stays undecided until onboarding finishes.
     */
    fun resolveOnboardingDone(prefs: QuizPrefs, stats: UserStats?): Boolean {
        if (prefs.onboardingDecided) return prefs.onboardingDone
        if (isExistingPlayer(stats, prefs.answeredTopics())) {
            prefs.onboardingDone = true
            return true
        }
        return false
    }

    fun startRoute(onboardingDone: Boolean): String =
        if (onboardingDone) Routes.DASHBOARD else Routes.ONBOARDING

    /**
     * Start route for this launch. [loadStats] returns the saved profile (null when none exists yet).
     * If the database is too slow we go to the dashboard without storing anything, so the decision is
     * retried next launch.
     */
    suspend fun resolveStartRoute(prefs: QuizPrefs, loadStats: suspend () -> UserStats?): String {
        if (prefs.onboardingDecided) return startRoute(prefs.onboardingDone)
        val stats: UserStats? = try {
            withTimeout(STATS_TIMEOUT_MS) { loadStats() }
        } catch (_: TimeoutCancellationException) {
            return Routes.DASHBOARD
        }
        return startRoute(resolveOnboardingDone(prefs, stats))
    }
}

/** A quiz screen with nothing to show, nothing coming and no error to explain (e.g. after process death). */
fun shouldRedirectFromQuiz(hasQuestions: Boolean, isLoading: Boolean, hasError: Boolean, finished: Boolean): Boolean =
    !hasQuestions && !isLoading && !hasError && !finished

/** Results / Review need a finished session with questions to show. */
fun shouldRedirectFromResults(finished: Boolean, hasQuestions: Boolean): Boolean =
    !finished || !hasQuestions

/** Bottom-tab navigation: one copy of each tab, state saved and restored, dashboard stays the base. */
fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens a screen without stacking a second copy on a double tap. */
fun NavController.navigateOnce(route: String) {
    navigate(route) { launchSingleTop = true }
}

/**
 * Goes back to the dashboard already in the back stack (falling back to a fresh one if there is none).
 * With [from] set, only acts while that route is still the visible one, so a late redirect from a
 * screen that is animating out can't pop whatever the user opened next.
 */
fun NavController.returnToDashboard(from: String? = null) {
    val current = currentDestination?.route
    if (current == Routes.DASHBOARD) return
    if (from != null && current != from) return
    val inStack = runCatching { getBackStackEntry(Routes.DASHBOARD) }.isSuccess
    if (inStack) {
        popBackStack(Routes.DASHBOARD, inclusive = false)
    } else {
        navigate(Routes.DASHBOARD) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}
