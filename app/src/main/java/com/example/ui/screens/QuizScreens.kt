package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt
import com.example.data.model.Question
import com.example.data.model.UserStats
import com.example.data.repository.DailyResult
import com.example.data.repository.PracticeSettings
import com.example.ui.SoundEffects
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.WrongRed

// --- 1. DAILY LOBBY SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeLobbyScreen(
    userStats: UserStats,
    dailyPlayed: Boolean,
    challengeNumber: Long,
    todayResult: DailyResult?,
    onNavigateBack: () -> Unit,
    onStartDailyQuiz: () -> Unit,
    onShare: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Challenge #$challengeNumber", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    if (dailyPlayed && todayResult != null) {
                        Text(
                            text = "Today's score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${todayResult.correct} / ${todayResult.answers.size}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // The same grid that gets shared, so players know what friends will see.
                        Text(
                            text = todayResult.answers.joinToString("") { if (it) "🟩" else "🟥" },
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Finished in ${todayResult.seconds / 60}m ${todayResult.seconds % 60}s · Streak: ${userStats.currentStreak} day${if (userStats.currentStreak == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = onShare,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("share_daily_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share with friends", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else if (dailyPlayed) {
                        Text(
                            text = "✓ Attempt used today",
                            style = MaterialTheme.typography.titleMedium.copy(color = CorrectGreen, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "A new challenge unlocks at midnight.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "Today's Trivia Special",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "10 questions, easy to hard. Everyone gets the same set today, so you can compare scores with friends.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = onStartDailyQuiz,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_daily_challenge_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Launch Challenge Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            Text(
                text = "How it works",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LobbyRule(Icons.Default.Groups, "Same questions for every player, every day — works offline too.")
                    LobbyRule(Icons.Default.TrendingUp, "6 trivia questions, 2 flags and 2 capitals, ramping from easy to hard.")
                    LobbyRule(Icons.Default.LockClock, "One attempt. Leaving after you've started uses it up.")
                    LobbyRule(Icons.Default.Share, "Share your 🟩🟥 grid without spoiling the answers.")
                    LobbyRule(Icons.Default.EmojiEvents, "A perfect score earns the Daily Champion badge.")
                }
            }
        }
    }
}

@Composable
private fun LobbyRule(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- 2. IN-QUIZ ENGINE SCREEN ---
@Composable
fun QuizPlayScreen(
    questions: List<Question>,
    currentIndex: Int,
    selectedOption: Int?,
    isRevealed: Boolean,
    timeLeft: Int,
    hiddenOptions: Set<Int>,
    audiencePoll: List<Int>?,
    userStats: UserStats,
    onSelectOption: (Int) -> Unit,
    onUse5050: () -> Unit,
    onUseSkip: () -> Unit,
    onUseExtraTime: () -> Unit,
    onUseAskAudience: () -> Unit,
    onQuitQuiz: () -> Unit,
    isLoading: Boolean = false,
    loadError: String? = null,
    isTimed: Boolean = true,
    isEndless: Boolean = false,
    onNext: () -> Unit = {},
    usedLifelines: Set<String> = emptySet(),
    timerSeconds: Int = PracticeSettings.QUESTION_SECONDS
) {
    val haptic = LocalHapticFeedback.current
    var showQuitDialog by remember { mutableStateOf(false) }
    val currentQ = questions.getOrNull(currentIndex)

    // System back goes through the same path as the X button, so the quiz is properly stopped
    // instead of running on (and being recorded as a failed session) in the background.
    BackHandler {
        if (currentQ == null) onQuitQuiz() else showQuitDialog = true
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text(if (isEndless) "End this run?" else "Quit quiz?") },
            text = {
                Text(
                    if (isEndless) "Your endless run will end here and your score will be saved."
                    else "Your progress in this quiz won't be saved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQuitDialog = false
                        onQuitQuiz()
                    },
                    modifier = Modifier.testTag("confirm_quit_quiz")
                ) { Text(if (isEndless) "End run" else "Quit", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Keep playing") }
            }
        )
    }

    if (currentQ == null) {
        QuizLoadingOrError(isLoading = isLoading || loadError == null, loadError = loadError, onBack = onQuitQuiz)
        return
    }

    // Correct / wrong tone when the answer is revealed ("Sound Effects" setting).
    LaunchedEffect(isRevealed, currentIndex) {
        if (isRevealed && userStats.isSoundEffectsEnabled) {
            if (selectedOption == currentQ.correctIndex) SoundEffects.correct() else SoundEffects.wrong()
        }
    }

    // Visual Timer bar depleting color logic (Green -> Yellow -> Red)
    val timerFraction = timeLeft / timerSeconds.toFloat()
    val timerColor = when {
        timerFraction > 0.5f -> CorrectGreen
        timerFraction > 0.25f -> GoldYellow
        else -> WrongRed
    }
    val timerProgress = timerFraction.coerceIn(0f, 1f) // +15s lifeline can push past the full bar

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Quit, Quiz Progress, and Visual Timer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showQuitDialog = true },
                        modifier = Modifier.testTag("quit_quiz_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Quit")
                    }
                    Text(
                        text = if (isEndless) "Question ${currentIndex + 1}" else "Question ${currentIndex + 1} of ${questions.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = {}) { // Placeholder balance space
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        )
                    }
                }

                // Dynamic Progress Bar (hidden when "Timed Questions" is switched off)
                if (isTimed) {
                    LinearProgressIndicator(
                        progress = { timerProgress },
                        color = timerColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // Central Dynamic Quiz Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animated Visibility with keyframe transition for sliding in from the right!
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "QuestionSlide"
                ) { slideIndex ->
                    // Each slide renders its own question, so the outgoing slide doesn't show the new one.
                    val currentQ = questions.getOrNull(slideIndex) ?: return@AnimatedContent
                    val isActiveSlide = slideIndex == currentIndex
                    val isRevealed = isRevealed && isActiveSlide
                    val selectedOption = if (isActiveSlide) selectedOption else null
                    val hiddenOptions = if (isActiveSlide) hiddenOptions else emptySet()
                    val audiencePoll = if (isActiveSlide) audiencePoll else null
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Category Label
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = currentQ.category.uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentQ.difficulty == "hard") WrongRed.copy(alpha = 0.15f) else GoldYellow.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = currentQ.difficulty.uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentQ.difficulty == "hard") WrongRed else Color(0xFF6B4B00)
                                    )
                                )
                            }
                        }

                        // Question Label Header
                        if (currentQ.category.startsWith("Flags")) {
                            val parts = currentQ.text.split("\n\n")
                            val instruction = parts.getOrNull(0) ?: currentQ.text
                            val value = parts.getOrNull(1) ?: ""

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = instruction,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (currentQ.category.contains("Flag to Country")) {
                                    // Massive central flag
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = value,
                                            style = androidx.compose.ui.text.TextStyle(fontSize = 72.sp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // Country name with global/map visual frame
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "🌎",
                                                fontSize = 28.sp
                                            )
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = currentQ.text,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 34.sp
                                ),
                                modifier = Modifier.testTag("in_quiz_question_label")
                            )
                        }

                        // If Ask Audience poll is open, show visually striking bar percentages!
                        if (audiencePoll != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("📊 Audience Poll Results", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    currentQ.optionsList.forEachIndexed { idx, option ->
                                        if (!hiddenOptions.contains(idx)) {
                                            val percent = audiencePoll.getOrNull(idx) ?: 0
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "${optionLabel(idx)}:",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.width(20.dp)
                                                )
                                                LinearProgressIndicator(
                                                    progress = { percent / 100f },
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(8.dp)
                                                        .clip(CircleShape)
                                                )
                                                Text(
                                                    text = "$percent%",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.width(36.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Options vertical panel
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            currentQ.optionsList.forEachIndexed { index, option ->
                                if (!hiddenOptions.contains(index)) {
                                    val isPickedByMe = selectedOption == index
                                    val isCorrectAlternative = index == currentQ.correctIndex

                                    // Dynamic Color Mapping based on reveal states
                                    val cardBg = when {
                                        !isRevealed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        isCorrectAlternative -> CorrectGreen.copy(alpha = 0.2f)
                                        isPickedByMe -> WrongRed.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                    }
                                    val cardBorderColor = when {
                                        !isRevealed -> Color.Transparent
                                        isCorrectAlternative -> CorrectGreen
                                        isPickedByMe -> WrongRed
                                        else -> Color.Transparent
                                    }

                                    Card(
                                        onClick = {
                                            if (!isRevealed && isActiveSlide) {
                                                // Short tactile vibrations on choosing an answer
                                                if (userStats.isHapticFeedbackEnabled) {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                }
                                                onSelectOption(index)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("quiz_option_${optionLabel(index).lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            // Option Prefix Icon indicator (A, B, C, D)
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            !isRevealed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.1f
                                                            )
                                                            isCorrectAlternative -> CorrectGreen
                                                            isPickedByMe -> WrongRed
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.05f
                                                            )
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isRevealed && isCorrectAlternative) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Correct", tint = Color.White)
                                                } else if (isRevealed && isPickedByMe) {
                                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Wrong", tint = Color.White)
                                                } else {
                                                    Text(
                                                        text = optionLabel(index),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }

                                            val isSingleFlagEmoji = currentQ.category == "Flags: Country to Flag"

                                            Text(
                                                text = option,
                                                style = if (isSingleFlagEmoji) {
                                                    MaterialTheme.typography.bodyLarge.copy(fontSize = 32.sp)
                                                } else {
                                                    MaterialTheme.typography.bodyLarge
                                                },
                                                fontWeight = if (isPickedByMe) FontWeight.ExtraBold else FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Learn something from every answer: show why, right away.
                        if (isRevealed && currentQ.explanation.isNotBlank()) {
                            AnswerExplanation(question = currentQ, selectedOption = selectedOption)
                        }
                    }
                }
            }

            // Bottom bar: lifelines while answering, "Next" once the answer is revealed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Divider()
                if (isRevealed) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("next_question_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (!isEndless && currentIndex + 1 >= questions.size) "See results" else "Next question",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Text(
                        text = "LIFELINES · ONE USE EACH PER QUIZ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LifelineButton(
                            label = "50/50",
                            count = if ("5050" in usedLifelines) 0 else 1,
                            icon = Icons.Default.FilterAlt, // standard representation
                            isActive = !isRevealed && hiddenOptions.isEmpty() && currentQ.optionsList.size >= 4,
                            onClick = onUse5050,
                            tag = "fifty_fifty_lifeline"
                        )
                        LifelineButton(
                            label = "Skip",
                            count = if ("skip" in usedLifelines) 0 else 1,
                            icon = Icons.Default.SkipNext,
                            isActive = !isRevealed,
                            onClick = onUseSkip,
                            tag = "skip_lifeline"
                        )
                        LifelineButton(
                            label = "+15s",
                            count = if ("time" in usedLifelines) 0 else 1,
                            icon = Icons.Default.Timer,
                            isActive = !isRevealed && isTimed,
                            onClick = onUseExtraTime,
                            tag = "extra_time_lifeline"
                        )
                        LifelineButton(
                            label = "Audience",
                            count = if ("audience" in usedLifelines) 0 else 1,
                            icon = Icons.Default.Poll,
                            isActive = !isRevealed && audiencePoll == null,
                            onClick = onUseAskAudience,
                            tag = "ask_audience_lifeline"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LifelineButton(
    label: String,
    count: Int,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = { if (isActive && count > 0) onClick() },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isActive && count > 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive && count > 0) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (count > 0) MaterialTheme.colorScheme.secondary else Color.Gray,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun AnswerExplanation(question: Question, selectedOption: Int?) {
    val gotItRight = selectedOption == question.correctIndex
    val accent = if (gotItRight) CorrectGreen else WrongRed
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("answer_explanation")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = when {
                    gotItRight -> "✓ Correct!"
                    selectedOption == null -> "⏱ Time's up"
                    else -> "✗ Not quite — the answer is ${question.optionsList.getOrNull(question.correctIndex).orEmpty()}"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = question.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** "A", "B", "C", ... without crashing if a question ever has more than four options. */
internal fun optionLabel(index: Int): String = ('A' + index).toString()

@Composable
private fun QuizLoadingOrError(isLoading: Boolean, loadError: String?, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (loadError != null) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = loadError,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBack, modifier = Modifier.testTag("quiz_error_back_button")) {
                    Text("Back", fontWeight = FontWeight.Bold)
                }
            } else if (isLoading) {
                CircularProgressIndicator()
                Text(
                    text = "Finding fresh questions…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        }
    }
}

// --- 3. RESULTS SCREEN ---
@Composable
fun QuizResultsScreen(
    questions: List<Question>,
    answers: List<Boolean>,
    xpEarned: Int,
    isDaily: Boolean,
    onReviewClick: () -> Unit,
    onDashboardFinish: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    val correctCount = answers.count { it }
    val totalCount = questions.size
    val accuracy = if (totalCount == 0) 0f else (correctCount.toFloat() / totalCount * 100)
    val isPerfect = totalCount > 0 && correctCount == totalCount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPerfect) GoldYellow.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPerfect) Icons.Default.EmojiEvents else Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = if (isPerfect) GoldYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }

            Text(
                text = if (isPerfect) "Blitz Perfect!" else "Session Complete!",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                textAlign = TextAlign.Center
            )

            // Special badge if perfect score (the Daily Champion badge only exists for the Daily Challenge)
            if (isPerfect) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldYellow.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isDaily) "👑 Daily Champion!" else "🎯 Perfect score: +${com.example.data.repository.Scoring.PERFECT_BONUS} XP bonus",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score breakdown cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ResultRow("Correct Answers", "$correctCount / $totalCount", CorrectGreen)
                    ResultRow("Accuracy Rate", "${accuracy.roundToInt()}%", MaterialTheme.colorScheme.primary)
                    ResultRow("XP Earned", "+$xpEarned XP", GoldYellow)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (onShare != null) {
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_result_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share your score", fontWeight = FontWeight.Bold)
                }
            }

            // Options
            Button(
                onClick = onReviewClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("review_answers_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Review Question Explanations", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onDashboardFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("results_back_to_dashboard_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Return to Dashboard", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}

// --- 4. REVIEW SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewAnswersScreen(
    questions: List<Question>,
    correctAnswers: List<Boolean>,
    selectedIndices: List<Int?>,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Questions", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            itemsIndexed(questions) { idx, q ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Question ${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val wasCorrect = correctAnswers.getOrNull(idx) ?: false
                            val noAnswer = !wasCorrect && selectedIndices.getOrNull(idx) == null
                            Text(
                                text = when {
                                    wasCorrect -> "✓ Correct"
                                    noAnswer -> "⏭ Skipped / timed out"
                                    else -> "✗ Incorrect"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    wasCorrect -> CorrectGreen
                                    noAnswer -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    else -> WrongRed
                                }
                            )
                        }

                        // Question Topic Label
                        Text(
                            text = q.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )

                        // Highlight Pick vs Right
                        val myPickIdx = selectedIndices.getOrNull(idx)
                        val isMyPickCorrect = myPickIdx == q.correctIndex

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            q.optionsList.forEachIndexed { optIdx, option ->
                                val isCorrectAlternative = optIdx == q.correctIndex
                                val isMySelection = optIdx == myPickIdx

                                val rowIndicatorColor = when {
                                    isCorrectAlternative -> CorrectGreen
                                    isMySelection -> WrongRed
                                    else -> Color.Transparent
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = rowIndicatorColor.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCorrectAlternative) CorrectGreen
                                                    else if (isMySelection) WrongRed
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCorrectAlternative) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = "Correct", tint = Color.White, modifier = Modifier.size(14.dp))
                                            } else if (isMySelection) {
                                                Icon(imageVector = Icons.Default.Close, contentDescription = "Wrong", tint = Color.White, modifier = Modifier.size(14.dp))
                                            } else {
                                                Text(optionLabel(optIdx), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        val isSingleFlagEmoji = q.category == "Flags: Country to Flag"

                                        Text(
                                            text = option,
                                            style = if (isSingleFlagEmoji) {
                                                MaterialTheme.typography.bodySmall.copy(fontSize = 20.sp)
                                            } else {
                                                MaterialTheme.typography.bodySmall
                                            },
                                            fontWeight = if (isMySelection) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Explanation Block (if not Flags)
                        if (!q.category.startsWith("Flags")) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Explanation:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = q.explanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
