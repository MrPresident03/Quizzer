package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Back-stack behaviour of the helpers the real nav host uses (same route names, same helpers). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationBackStackTest {

    @get:Rule val compose = createComposeRule()
    private lateinit var nav: TestNavHostController

    private fun launch(start: String) {
        nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        compose.setContent {
            NavHost(nav, startDestination = start) {
                for (route in listOf(
                    Routes.DASHBOARD, Routes.ONBOARDING, "practice_config", "analytics_milestones",
                    "settings_config", "daily_lobby", "practice_quiz", "practice_results", "review_answers"
                )) composable(route) { Empty() }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Empty() {}

    private fun stack(): List<String> {
        compose.waitForIdle()
        return nav.currentBackStack.value
            .filter { it.destination !is NavGraph }
            .mapNotNull { it.destination.route }
    }

    private fun run(block: () -> Unit) {
        compose.runOnIdle(block)
        compose.waitForIdle()
    }

    @Test
    fun tabsNeverStackUpSoBackFromHomeLeavesTheGraph() {
        launch(Routes.DASHBOARD)
        run { nav.navigateTab("practice_config") }
        run { nav.navigateTab("analytics_milestones") }
        run { nav.navigateTab("settings_config") }
        run { nav.navigateTab(Routes.DASHBOARD) }
        run { nav.navigateTab(Routes.DASHBOARD) } // tapping Home on Home
        assertEquals(listOf(Routes.DASHBOARD), stack())
    }

    @Test
    fun tabsStackOnTopOfDashboardOnly() {
        launch(Routes.DASHBOARD)
        run { nav.navigateTab("practice_config") }
        run { nav.navigateTab("settings_config") }
        assertEquals(listOf(Routes.DASHBOARD, "settings_config"), stack())
    }

    @Test
    fun doubleTappedQuizStartPushesOneQuiz() {
        launch(Routes.DASHBOARD)
        run {
            nav.navigateOnce("practice_quiz")
            nav.navigateOnce("practice_quiz")
        }
        assertEquals(listOf(Routes.DASHBOARD, "practice_quiz"), stack())
    }

    @Test
    fun finishedQuizBackNeverReentersItAndReturnLeavesOneDashboard() {
        launch(Routes.DASHBOARD)
        run { nav.navigateTab("practice_config") }
        run { nav.navigateOnce("practice_quiz") }
        run {
            nav.navigate("practice_results") {
                popUpTo("practice_quiz") { inclusive = true }
                launchSingleTop = true
            }
        }
        run { nav.navigateOnce("review_answers") }
        assertEquals(listOf(Routes.DASHBOARD, "practice_config", "practice_results", "review_answers"), stack())

        run { nav.popBackStack() } // Back from review
        run { nav.popBackStack() } // Back from results
        assertEquals(listOf(Routes.DASHBOARD, "practice_config"), stack())

        run { nav.navigateOnce("practice_quiz") }
        run { nav.navigate("practice_results") { popUpTo("practice_quiz") { inclusive = true } } }
        run { nav.returnToDashboard() } // "Return to Dashboard"
        assertEquals(listOf(Routes.DASHBOARD), stack())
    }

    @Test
    fun returnToDashboardWithoutOneBuildsASingleDashboard() {
        launch(Routes.ONBOARDING)
        run { nav.returnToDashboard() }
        assertEquals(listOf(Routes.DASHBOARD), stack())
    }

    @Test
    fun lateRedirectFromAScreenThatIsNoLongerShownDoesNothing() {
        launch(Routes.DASHBOARD)
        run { nav.navigateOnce("practice_quiz") }
        run { nav.returnToDashboard(from = "practice_results") }
        assertEquals(listOf(Routes.DASHBOARD, "practice_quiz"), stack())
        run { nav.returnToDashboard(from = "practice_quiz") }
        assertEquals(listOf(Routes.DASHBOARD), stack())
    }
}
