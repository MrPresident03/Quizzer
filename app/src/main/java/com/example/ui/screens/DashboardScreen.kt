package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserStats
import com.example.data.repository.Scoring
import com.example.ui.theme.GoldYellow
import java.time.LocalDate
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userStats: UserStats,
    dailyPlayed: Boolean,
    onNavigateToDailyChallenge: () -> Unit,
    onNavigateToPracticeSelection: () -> Unit,
    onStartTopicOfTheDay: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    quickPlaySummary: String = "10 questions · All topics · Mixed difficulty",
    onQuickPlay: () -> Unit = {},
    lifetimeXp: Int = userStats.xpPoints,
    playedDays: Set<LocalDate> = emptySet(),
    mistakeCount: Int = 0,
    onStartMistakeReview: () -> Unit = {}
) {
    // Midnight countdown timer
    var timeUntilMidnight by remember { mutableStateOf("") }
    LaunchedEffect(key1 = true) {
        while (true) {
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMs = midnight.timeInMillis - now.timeInMillis
            val diffSeconds = (diffMs / 1000) % 60
            val diffMinutes = (diffMs / (1000 * 60)) % 60
            val diffHours = (diffMs / (1000 * 60 * 60)) % 24
            timeUntilMidnight = String.format("%02d:%02d:%02d", diffHours, diffMinutes, diffSeconds)
            delay(1000)
        }
    }

    // Dynamic Title based on Level
    val userTitle = when {
        userStats.currentLevel >= 40 -> "Universal Sage"
        userStats.currentLevel >= 30 -> "Trivia Oracle"
        userStats.currentLevel >= 20 -> "Grand Master"
        userStats.currentLevel >= 12 -> "Quiz Wizard"
        userStats.currentLevel >= 6 -> "Intellect Pro"
        else -> "Quiz Newbie"
    }

    // Topic of the day changes dynamically depending on weekday
    val weekdays = listOf(
        "History" to Icons.Default.History,
        "Science & Nature" to Icons.Default.Science,
        "Geography" to Icons.Default.Public,
        "Sports & Recreation" to Icons.Default.SportsBasketball,
        "Entertainment" to Icons.Default.Movie,
        "Technology & Computing" to Icons.Default.Computer,
        "General Knowledge" to Icons.Default.MenuBook
    )
    val dayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % weekdays.size
    val (topicOfTheDay, topicIcon) = weekdays[dayIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val initialLetter = (userStats.displayName.firstOrNull() ?: 'U').toString().uppercase()
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1), // Indigo 500
                                            Color(0xFFA855F7)  // Purple 500
                                        )
                                    )
                                )
                                .clickable { onNavigateToProfile() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Column {
                            Text(
                                text = "Hi, ${userStats.displayName}!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$userTitle (Level ${userStats.currentLevel})",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0xFFFFF7ED)) // bg-orange-50
                            .background(Color(0xFFFFF7ED))
                            .border(width = 1.dp, color = Color(0xFFFFEDD5), shape = RoundedCornerShape(100.dp)) // border-orange-100
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🔥 ${userStats.currentStreak} Days",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C) // text-orange-600
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Quick Play: one tap into a quiz with your last practice settings
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_play_card"),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quick Play",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Text(
                            text = quickPlaySummary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onQuickPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onPrimary,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("quick_play_button")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PLAY", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                            }
                            TextButton(
                                onClick = onNavigateToPracticeSelection,
                                modifier = Modifier.testTag("quick_play_settings_button")
                            ) {
                                Text(
                                    text = "Change",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Daily Challenge Prominent Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_challenge_card")
                        .clickable { onNavigateToDailyChallenge() }
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(28.dp)
                        ),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "DAILY CHALLENGE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Live pulsating green/red dot mock (or small active circle indicator)
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (dailyPlayed) Color.Gray else Color(0xFFEF4444)) // red-500
                                    )
                                    Text(
                                        text = if (dailyPlayed) "Completed" else "Ends in $timeUntilMidnight",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Ultimate World Trivia Mix",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (dailyPlayed) "Done for today! Share your score grid and come back after midnight."
                                else "Same 10 questions for everyone today • Easy to hard • One attempt. Compare scores with friends!",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    lineHeight = 20.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onNavigateToDailyChallenge,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (dailyPlayed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary,
                                    contentColor = if (dailyPlayed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (dailyPlayed) "VIEW & SHARE RESULT" else "PLAY NOW",
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Play",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Review mistakes: questions you got wrong come back until you get them right
            if (mistakeCount > 0) {
                item {
                    Card(
                        onClick = onStartMistakeReview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_mistakes_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Review your mistakes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "$mistakeCount question${if (mistakeCount == 1) "" else "s"} to master · they clear when you get them right",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Start review",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Level progress bar
            item {
                // Progress toward the next level comes from lifetime XP, not the spendable balance.
                val progressPercent = if (userStats.currentLevel >= Scoring.MAX_LEVEL) 1f
                else (lifetimeXp % Scoring.XP_PER_LEVEL) / Scoring.XP_PER_LEVEL.toFloat()
                val xpToNext = Scoring.XP_PER_LEVEL - (lifetimeXp % Scoring.XP_PER_LEVEL)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "GLOBAL LEVEL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${userStats.currentLevel}",
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                    Text(
                                        text = userTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "TOTAL XP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = "$lifetimeXp XP",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { progressPercent },
                            trackColor = Color.White.copy(alpha = 0.2f),
                            color = Color.White,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        if (userStats.currentLevel < Scoring.MAX_LEVEL) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$xpToNext XP to level ${userStats.currentLevel + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f))
                            )
                        }
                    }
                }
            }

            // Streak card + Visual Streak Calendar
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "${userStats.currentStreak}-Day Active Blitz!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (userStats.hasStreakFreeze > 0) "🧊 ${userStats.hasStreakFreeze} streak freeze${if (userStats.hasStreakFreeze == 1) "" else "s"} ready: covers a missed day" else "Play 7 days in a row to earn a streak freeze",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Streak Calendar Layout representing the past 7 days
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // The last 7 days, oldest first, ticked on days you actually played.
                            val today = LocalDate.now()
                            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                            val list = days.map {
                                it.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                            }
                            val activeFlags = days.map { it in playedDays }

                            for (i in 0..6) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = list[i],
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (activeFlags[i]) Color(0xFFE65100)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (activeFlags[i]) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Topic of the Day Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartTopicOfTheDay(topicOfTheDay) }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = topicIcon,
                                contentDescription = topicOfTheDay,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Topic of the Day",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                            Text(
                                text = topicOfTheDay,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Text(
                                text = "A fresh 10-question quiz on today's featured topic",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = color)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                )
            }
        }
    }
}
