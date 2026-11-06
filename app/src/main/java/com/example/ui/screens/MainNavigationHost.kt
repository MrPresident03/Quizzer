package com.example.ui.screens

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.QuizApplication
import com.example.data.repository.DailyChallenge
import com.example.data.repository.QuizPrefs
import com.example.data.repository.Scoring
import com.example.data.repository.SessionType
import com.example.ui.Routes
import com.example.ui.navigateOnce
import com.example.ui.navigateTab
import com.example.ui.returnToDashboard
import com.example.ui.shouldRedirectFromQuiz
import com.example.ui.shouldRedirectFromResults
import com.example.ui.shareText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.ui.viewmodel.QuizViewModel
import com.example.ui.viewmodel.QuizViewModelFactory
import com.example.ui.theme.BrainBlitzTheme

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainNavigationHost(startDestination: String = Routes.DASHBOARD) {
    val navController = rememberNavController()

    // Retrieve manual DI instances from custom Application Context
    val context = LocalContext.current.applicationContext as QuizApplication
    val mainViewModel: QuizViewModel = viewModel(
        factory = QuizViewModelFactory(context, context.repository)
    )

    val stats by mainViewModel.userStats.collectAsState()
    val history by mainViewModel.quizHistory.collectAsState()
    val achievements by mainViewModel.achievements.collectAsState()
    val dailyPlayed by mainViewModel.dailyPlayedToday.collectAsState()
    val todayDailyResult by mainViewModel.todayDailyResult.collectAsState()
    val mistakeCount by mainViewModel.mistakeCount.collectAsState()
    val topicStats by mainViewModel.topicStats.collectAsState()
    val reminderEnabled by mainViewModel.dailyReminderEnabled.collectAsState()
    val quickPlay by mainViewModel.quickPlaySettings.collectAsState()
    val quizNotice by mainViewModel.notice.collectAsState()
    val endMessage by mainViewModel.endMessage.collectAsState()
    val activityContext = LocalContext.current
    val onboardingPrefs = remember { QuizPrefs(context) }

    // Derived from real history: lifetime XP (level progress) and the days you actually played.
    val lifetimeXp = remember(history) { history.sumOf { it.xpEarned } }
    val playedDays = remember(history) {
        val zone = ZoneId.systemDefault()
        history.mapTo(HashSet()) { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    }
    val shareDaily: () -> Unit = {
        mainViewModel.dailyShareText()?.let { shareText(activityContext, it) }
    }

    // Handle Active Theme Wrap dynamically!
    BrainBlitzTheme(themeId = stats.activeThemeId, largeText = stats.fontSizePreference == "large") {
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        // Determine when to display the Bottom Navigation Row (hide during splash, onboarding, and active quizzes!)
        val showBottomBar = currentRoute in listOf(
            "dashboard",
            "practice_config",
            "analytics_milestones",
            "settings_config"
        )

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.testTag("app_bottom_nav_bar")
                    ) {
                        // Tab 1: Home Dashboard
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "dashboard") Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = {
                                Text(
                                    text = "Home",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "dashboard",
                            onClick = {
                                navController.navigateTab("dashboard")
                            },
                            modifier = Modifier.testTag("nav_tab_home")
                        )

                        // Tab 2: Customize Practice Selection
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "practice_config") Icons.Default.MenuBook else Icons.Outlined.MenuBook,
                                    contentDescription = "Practice"
                                )
                            },
                            label = {
                                Text(
                                    text = "Practice",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "practice_config",
                            onClick = {
                                navController.navigateTab("practice_config")
                            },
                            modifier = Modifier.testTag("nav_tab_practice")
                        )

                        // Tab 3: Analytics / Leaderboards / Achievements
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "analytics_milestones") Icons.Default.BarChart else Icons.Outlined.BarChart,
                                    contentDescription = "Analytics"
                                )
                            },
                            label = {
                                Text(
                                    text = "Progress",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "analytics_milestones",
                            onClick = {
                                navController.navigateTab("analytics_milestones")
                            },
                            modifier = Modifier.testTag("nav_tab_analytics")
                        )

                        // Tab 4: Profile, theme and preferences
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "settings_config") Icons.Default.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    text = "Settings",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "settings_config",
                            onClick = {
                                navController.navigateTab("settings_config")
                            },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // Tab screens have their own Scaffolds/top bars: the outer padding already
                        // covers the status bar, so mark it consumed or it is applied twice.
                        if (showBottomBar) Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                        else Modifier
                    )
            ) {
                // Route B: Swipe Onboarding Slides Panels
                composable("onboarding") {
                    OnboardingScreen {
                        onboardingPrefs.onboardingDone = true
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                // Route C: Main Dashboard Screen Tab 1
                composable("dashboard") {
                    // Re-check so the daily lock clears after midnight without restarting the app.
                    LaunchedEffect(Unit) { mainViewModel.checkDailyPlayedStatus() }
                    DashboardScreen(
                        userStats = stats,
                        dailyPlayed = dailyPlayed,
                        onNavigateToDailyChallenge = {
                            navController.navigateOnce("daily_lobby")
                        },
                        onNavigateToPracticeSelection = {
                            navController.navigateTab("practice_config")
                        },
                        onNavigateToProfile = {
                            navController.navigateTab("settings_config")
                        },
                        quickPlaySummary = quickPlay.summary(),
                        onQuickPlay = {
                            mainViewModel.startQuickPlay()
                            navController.navigateOnce("practice_quiz")
                        },
                        lifetimeXp = lifetimeXp,
                        playedDays = playedDays,
                        mistakeCount = mistakeCount,
                        onStartMistakeReview = {
                            mainViewModel.startMistakeReview()
                            navController.navigateOnce("practice_quiz")
                        },
                        challengeNumber = DailyChallenge.number(LocalDate.now()),
                        todayResult = todayDailyResult,
                        onShareDaily = shareDaily
                    )
                }

                // Route D: Config Topic Selector Screen Tab 2
                composable("practice_config") {
                    TopicSelectionScreen(
                        onStartQuiz = { categories, difficulty, count, format, timed ->
                            mainViewModel.startPracticeQuiz(categories, difficulty, count, format, timed, rememberForQuickPlay = true)
                            navController.navigateOnce("practice_quiz")
                        },
                        initial = quickPlay,
                        onStartFlagQuiz = { mode, difficulty, count ->
                            mainViewModel.startFlagsQuiz(mode, difficulty, count)
                            navController.navigateOnce("practice_quiz")
                        }
                    )
                }

                // Route E: Analytics achievements Screen Tab 3
                composable("analytics_milestones") {
                    LaunchedEffect(Unit) { mainViewModel.refreshTopicStats() }
                    StatsAndAchievementsScreen(
                        userStats = stats,
                        historyList = history,
                        achievementList = achievements,
                        topicStats = topicStats,
                        onPracticeTopic = { topic ->
                            if (topic == "Flags") {
                                mainViewModel.startFlagsQuiz("Mixed", "Mixed", 10)
                            } else {
                                mainViewModel.startPracticeQuiz(listOf(topic), "Mixed", 10, "Mixed")
                            }
                            navController.navigateOnce("practice_quiz")
                        }
                    )
                }

                // Route G: App Preferences Toggles Tab 5
                composable("settings_config") {
                    SettingsScreen(
                        userStats = stats,
                        onUpdateToggles = { sound, haptic, textFont ->
                            mainViewModel.updateToggles(sound, haptic, textFont)
                        },
                        onResetProgress = {
                            mainViewModel.resetAllAppProgress()
                            onboardingPrefs.onboardingDone = false
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onClearQuestionHistory = {
                            mainViewModel.clearHistoryAndRepeats()
                        },
                        reminderEnabled = reminderEnabled,
                        onReminderChange = { mainViewModel.setDailyReminder(it) },
                        onUpdateProfile = { name, avatarId -> mainViewModel.updateProfile(name, avatarId) },
                        onSelectTheme = { mainViewModel.selectActiveTheme(it) }
                    )
                }

                // Route H: Daily Challenge lobby
                composable("daily_lobby") {
                    DailyChallengeLobbyScreen(
                        userStats = stats,
                        dailyPlayed = dailyPlayed,
                        challengeNumber = DailyChallenge.number(LocalDate.now()),
                        todayResult = todayDailyResult,
                        onNavigateBack = { navController.popBackStack() },
                        onStartDailyQuiz = {
                            mainViewModel.startDailyChallenge()
                            navController.navigateOnce("daily_quiz")
                        },
                        onShare = shareDaily
                    )
                }

                // Route I: Daily timing interactive Quiz play
                composable("daily_quiz") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    val activeIdx by mainViewModel.currentQuestionIndex.collectAsState()
                    val activeSelection by mainViewModel.currentSelectedOption.collectAsState()
                    val activeReveal by mainViewModel.isAnswerRevealed.collectAsState()
                    val activeTimeLeft by mainViewModel.timeLeft.collectAsState()
                    val quizEnded by mainViewModel.quizFinished.collectAsState()
                    val hiddenOpts by mainViewModel.hiddenOptions.collectAsState()
                    val audPoll by mainViewModel.audiencePoll.collectAsState()
                    val loading by mainViewModel.isLoading.collectAsState()
                    val error by mainViewModel.loadError.collectAsState()
                    val timed by mainViewModel.isTimed.collectAsState()
                    val type by mainViewModel.sessionType.collectAsState()
                    val usedLifelines by mainViewModel.usedLifelines.collectAsState()

                    // Nothing to play (process death, say): leave instead of spinning forever.
                    LaunchedEffect(activeQs.isEmpty(), loading, error, quizEnded) {
                        if (shouldRedirectFromQuiz(activeQs.isNotEmpty(), loading, error != null, quizEnded)) {
                            navController.returnToDashboard(from = "daily_quiz")
                        }
                    }

                    LaunchedEffect(quizEnded) {
                        if (quizEnded) {
                            navController.navigate("daily_results") {
                                popUpTo("daily_quiz") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    QuizPlayScreen(
                        isDaily = true,
                        notice = quizNotice,
                        onDismissNotice = { mainViewModel.dismissNotice() },
                        onPauseTimer = { mainViewModel.pauseTimer() },
                        onResumeTimer = { mainViewModel.resumeTimer() },
                        questions = activeQs,
                        currentIndex = activeIdx,
                        selectedOption = activeSelection,
                        isRevealed = activeReveal,
                        timeLeft = { activeTimeLeft },
                        hiddenOptions = hiddenOpts,
                        audiencePoll = audPoll,
                        userStats = stats,
                        onSelectOption = { mainViewModel.selectOption(it) },
                        onUse5050 = { mainViewModel.useLifeline5050() },
                        onUseSkip = { mainViewModel.useLifelineSkip() },
                        onUseExtraTime = { mainViewModel.useLifelineExtraTime() },
                        onUseAskAudience = { mainViewModel.useLifelineAskAudience() },
                        onQuitQuiz = {
                            // Stops the timer and pending jobs. Endless runs end on a results screen instead.
                            if (!mainViewModel.quitQuiz()) {
                                navController.returnToDashboard()
                            }
                        },
                        isLoading = loading,
                        loadError = error,
                        isTimed = timed,
                        isEndless = type == SessionType.ENDLESS,
                        onNext = { mainViewModel.continueToNext() },
                        usedLifelines = usedLifelines
                    )
                }

                // Route J: Practice custom timing Quiz play
                composable("practice_quiz") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    val activeIdx by mainViewModel.currentQuestionIndex.collectAsState()
                    val activeSelection by mainViewModel.currentSelectedOption.collectAsState()
                    val activeReveal by mainViewModel.isAnswerRevealed.collectAsState()
                    val activeTimeLeft by mainViewModel.timeLeft.collectAsState()
                    val quizEnded by mainViewModel.quizFinished.collectAsState()
                    val hiddenOpts by mainViewModel.hiddenOptions.collectAsState()
                    val audPoll by mainViewModel.audiencePoll.collectAsState()
                    val loading by mainViewModel.isLoading.collectAsState()
                    val error by mainViewModel.loadError.collectAsState()
                    val timed by mainViewModel.isTimed.collectAsState()
                    val type by mainViewModel.sessionType.collectAsState()
                    val usedLifelines by mainViewModel.usedLifelines.collectAsState()

                    // Nothing to play (process death, say): leave instead of spinning forever.
                    LaunchedEffect(activeQs.isEmpty(), loading, error, quizEnded) {
                        if (shouldRedirectFromQuiz(activeQs.isNotEmpty(), loading, error != null, quizEnded)) {
                            navController.returnToDashboard(from = "practice_quiz")
                        }
                    }

                    LaunchedEffect(quizEnded) {
                        if (quizEnded) {
                            navController.navigate("practice_results") {
                                popUpTo("practice_quiz") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    QuizPlayScreen(
                        isDaily = false,
                        notice = quizNotice,
                        onDismissNotice = { mainViewModel.dismissNotice() },
                        onPauseTimer = { mainViewModel.pauseTimer() },
                        onResumeTimer = { mainViewModel.resumeTimer() },
                        questions = activeQs,
                        currentIndex = activeIdx,
                        selectedOption = activeSelection,
                        isRevealed = activeReveal,
                        timeLeft = { activeTimeLeft },
                        hiddenOptions = hiddenOpts,
                        audiencePoll = audPoll,
                        userStats = stats,
                        onSelectOption = { mainViewModel.selectOption(it) },
                        onUse5050 = { mainViewModel.useLifeline5050() },
                        onUseSkip = { mainViewModel.useLifelineSkip() },
                        onUseExtraTime = { mainViewModel.useLifelineExtraTime() },
                        onUseAskAudience = { mainViewModel.useLifelineAskAudience() },
                        onQuitQuiz = {
                            // Stops the timer and pending jobs. Endless runs end on a results screen instead.
                            if (!mainViewModel.quitQuiz()) {
                                navController.returnToDashboard()
                            }
                        },
                        isLoading = loading,
                        loadError = error,
                        isTimed = timed,
                        isEndless = type == SessionType.ENDLESS,
                        onNext = { mainViewModel.continueToNext() },
                        usedLifelines = usedLifelines
                    )
                }

                // Route K: Daily results summary card
                composable("daily_results") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    val xpEarned by mainViewModel.lastSessionXp.collectAsState()
                    val type by mainViewModel.sessionType.collectAsState()
                    val finished by mainViewModel.quizFinished.collectAsState()
                    LaunchedEffect(finished, activeQs.isEmpty()) {
                        if (shouldRedirectFromResults(finished, activeQs.isNotEmpty())) {
                            navController.returnToDashboard(from = "daily_results")
                        }
                    }
                    QuizResultsScreen(
                        questions = activeQs,
                        answers = mainViewModel.quizAnswersList,
                        xpEarned = xpEarned,
                        isDaily = type == SessionType.DAILY,
                        perfectEligible = Scoring.isPerfect(type, activeQs.size, mainViewModel.quizAnswersList),
                        endMessage = endMessage,
                        onReviewClick = { navController.navigateOnce("review_answers") },
                        onDashboardFinish = { navController.returnToDashboard() },
                        onShare = shareDaily
                    )
                }

                // Route L: Practice results summary card
                composable("practice_results") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    val xpEarned by mainViewModel.lastSessionXp.collectAsState()
                    val type by mainViewModel.sessionType.collectAsState()
                    val finished by mainViewModel.quizFinished.collectAsState()
                    LaunchedEffect(finished, activeQs.isEmpty()) {
                        if (shouldRedirectFromResults(finished, activeQs.isNotEmpty())) {
                            navController.returnToDashboard(from = "practice_results")
                        }
                    }
                    QuizResultsScreen(
                        questions = activeQs,
                        answers = mainViewModel.quizAnswersList,
                        xpEarned = xpEarned,
                        isDaily = type == SessionType.DAILY,
                        perfectEligible = Scoring.isPerfect(type, activeQs.size, mainViewModel.quizAnswersList),
                        endMessage = endMessage,
                        onReviewClick = { navController.navigateOnce("review_answers") },
                        onDashboardFinish = { navController.returnToDashboard() }
                    )
                }

                // Route M: Session detailed question-by-question explanations review
                composable("review_answers") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    val finished by mainViewModel.quizFinished.collectAsState()
                    LaunchedEffect(finished, activeQs.isEmpty()) {
                        if (shouldRedirectFromResults(finished, activeQs.isNotEmpty())) {
                            navController.returnToDashboard(from = "review_answers")
                        }
                    }
                    ReviewAnswersScreen(
                        questions = activeQs,
                        correctAnswers = mainViewModel.quizAnswersList,
                        selectedIndices = mainViewModel.quizSelectedIndices,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
