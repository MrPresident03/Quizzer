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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.QuizApplication
import com.example.ui.viewmodel.QuizViewModel
import com.example.ui.viewmodel.QuizViewModelFactory
import com.example.ui.theme.BrainBlitzTheme

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainNavigationHost() {
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

    // Handle Active Theme Wrap dynamically!
    BrainBlitzTheme(themeId = stats.activeThemeId) {
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        // Determine when to display the Bottom Navigation Row (hide during splash, onboarding, and active quizzes!)
        val showBottomBar = currentRoute in listOf(
            "dashboard",
            "practice_config",
            "analytics_milestones",
            "profile_shop",
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
                                navController.navigate("dashboard") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                                navController.navigate("practice_config") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                                    text = "Milestones",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "analytics_milestones",
                            onClick = {
                                navController.navigate("analytics_milestones") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.testTag("nav_tab_analytics")
                        )

                        // Tab 4: Profile customizers and XP shop
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "profile_shop") Icons.Default.Person else Icons.Outlined.Person,
                                    contentDescription = "Shop"
                                )
                            },
                            label = {
                                Text(
                                    text = "Shop",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "profile_shop",
                            onClick = {
                                navController.navigate("profile_shop") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.testTag("nav_tab_shop")
                        )

                        // Tab 5: General Preferences App configs
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "settings_config") Icons.Default.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    text = "System",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                )
                            },
                            selected = currentRoute == "settings_config",
                            onClick = {
                                navController.navigate("settings_config") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (showBottomBar) innerPadding else PaddingValues(0.dp))
            ) {
                // Route A: Splash Screen Pulsing Logo
                composable("splash") {
                    SplashScreen(themeId = stats.activeThemeId) {
                        // After splash, route straight to onboarding if first run (such as stats count equals initial zeros), otherwise Home
                        if (stats.totalQuestionsAnswered == 0 && stats.xpPoints == 0 && stats.displayName == "Quiz Champ") {
                            navController.navigate("onboarding") {
                                popUpTo("splash") { inclusive = true }
                            }
                        } else {
                            navController.navigate("dashboard") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }
                }

                // Route B: Swipe Onboarding Slides Panels
                composable("onboarding") {
                    OnboardingScreen {
                        navController.navigate("dashboard") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                }

                // Route C: Main Dashboard Screen Tab 1
                composable("dashboard") {
                    DashboardScreen(
                        userStats = stats,
                        dailyPlayed = dailyPlayed,
                        onNavigateToDailyChallenge = {
                            navController.navigate("daily_lobby")
                        },
                        onNavigateToPracticeSelection = {
                            navController.navigate("practice_config")
                        },
                        onStartTopicOfTheDay = { topic ->
                            mainViewModel.startPracticeQuiz(listOf(topic), "Mixed", 10, "Mixed")
                            navController.navigate("practice_quiz")
                        },
                        onNavigateToProfile = {
                            navController.navigate("profile_shop")
                        },
                        onStartFlagQuiz = { mode, difficulty, count ->
                            mainViewModel.startFlagsQuiz(mode, difficulty, count)
                            navController.navigate("practice_quiz")
                        }
                    )
                }

                // Route D: Config Topic Selector Screen Tab 2
                composable("practice_config") {
                    TopicSelectionScreen(
                        onNavigateBack = {
                            navController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        },
                        onStartQuiz = { categories, difficulty, count, format ->
                            mainViewModel.startPracticeQuiz(categories, difficulty, count, format)
                            navController.navigate("practice_quiz")
                        }
                    )
                }

                // Route E: Analytics achievements Screen Tab 3
                composable("analytics_milestones") {
                    StatsAndAchievementsScreen(
                        userStats = stats,
                        historyList = history,
                        achievementList = achievements
                    )
                }

                // Route F: Profile customized and Shop Tab 4
                composable("profile_shop") {
                    ProfileAndShopScreen(
                        userStats = stats,
                        onUpdateProfile = { name, avatarId ->
                            mainViewModel.updateProfile(name, avatarId)
                        },
                        onBuyLifeline = { type, cost ->
                            mainViewModel.buyLifelineItem(type, cost)
                        },
                        onBuyTheme = { id, cost ->
                            mainViewModel.buyOrUnlockTheme(id, cost)
                        },
                        onSelectTheme = { themeId ->
                            mainViewModel.selectActiveTheme(themeId)
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
                            navController.navigate("onboarding") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        },
                        onClearQuestionHistory = {
                            mainViewModel.clearHistoryAndRepeats()
                        }
                    )
                }

                // Route H: Daily Challenge lobby
                composable("daily_lobby") {
                    DailyChallengeLobbyScreen(
                        userStats = stats,
                        dailyPlayed = dailyPlayed,
                        onNavigateBack = { navController.popBackStack() },
                        onStartDailyQuiz = {
                            mainViewModel.startDailyChallenge()
                            navController.navigate("daily_quiz")
                        }
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

                    LaunchedEffect(quizEnded) {
                        if (quizEnded) {
                            navController.navigate("daily_results") {
                                popUpTo("daily_quiz") { inclusive = true }
                            }
                        }
                    }

                    QuizPlayScreen(
                        questions = activeQs,
                        currentIndex = activeIdx,
                        selectedOption = activeSelection,
                        isRevealed = activeReveal,
                        timeLeft = activeTimeLeft,
                        hiddenOptions = hiddenOpts,
                        audiencePoll = audPoll,
                        userStats = stats,
                        onSelectOption = { mainViewModel.selectOption(it) },
                        onUse5050 = { mainViewModel.useLifeline5050() },
                        onUseSkip = { mainViewModel.useLifelineSkip() },
                        onUseExtraTime = { mainViewModel.useLifelineExtraTime() },
                        onUseAskAudience = { mainViewModel.useLifelineAskAudience() },
                        onQuitQuiz = {
                            navController.navigate("dashboard") {
                                popUpTo("daily_quiz") { inclusive = true }
                            }
                        }
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

                    LaunchedEffect(quizEnded) {
                        if (quizEnded) {
                            navController.navigate("practice_results") {
                                popUpTo("practice_quiz") { inclusive = true }
                            }
                        }
                    }

                    QuizPlayScreen(
                        questions = activeQs,
                        currentIndex = activeIdx,
                        selectedOption = activeSelection,
                        isRevealed = activeReveal,
                        timeLeft = activeTimeLeft,
                        hiddenOptions = hiddenOpts,
                        audiencePoll = audPoll,
                        userStats = stats,
                        onSelectOption = { mainViewModel.selectOption(it) },
                        onUse5050 = { mainViewModel.useLifeline5050() },
                        onUseSkip = { mainViewModel.useLifelineSkip() },
                        onUseExtraTime = { mainViewModel.useLifelineExtraTime() },
                        onUseAskAudience = { mainViewModel.useLifelineAskAudience() },
                        onQuitQuiz = {
                            navController.navigate("dashboard") {
                                popUpTo("practice_quiz") { inclusive = true }
                            }
                        }
                    )
                }

                // Route K: Daily results summary card
                composable("daily_results") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    QuizResultsScreen(
                        questions = activeQs,
                        answers = mainViewModel.quizAnswersList,
                        onReviewClick = { navController.navigate("review_answers") },
                        onDashboardFinish = {
                            navController.navigate("dashboard") {
                                popUpTo("daily_results") { inclusive = true }
                            }
                        }
                    )
                }

                // Route L: Practice results summary card
                composable("practice_results") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
                    QuizResultsScreen(
                        questions = activeQs,
                        answers = mainViewModel.quizAnswersList,
                        onReviewClick = { navController.navigate("review_answers") },
                        onDashboardFinish = {
                            navController.navigate("dashboard") {
                                popUpTo("practice_results") { inclusive = true }
                            }
                        }
                    )
                }

                // Route M: Session detailed question-by-question explanations review
                composable("review_answers") {
                    val activeQs by mainViewModel.activeQuestions.collectAsState()
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
