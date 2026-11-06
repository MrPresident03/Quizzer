@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UserStats
import com.example.data.repository.DailyResult
import com.example.data.repository.Scoring
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.delay

/** "1 day" / "2 days". */
fun streakLabel(days: Int): String = if (days == 1) "1 day" else "$days days"

/** "Ends in 4h 12m" style countdown to local midnight. */
fun timeLeftToday(now: LocalDateTime): String {
    val minutes = ChronoUnit.MINUTES.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
fun DashboardScreen(
    userStats: UserStats,
    dailyPlayed: Boolean,
    onNavigateToDailyChallenge: () -> Unit,
    onNavigateToPracticeSelection: () -> Unit,
    onNavigateToProfile: () -> Unit,
    quickPlaySummary: String = "10 questions · All topics · Mixed difficulty",
    onQuickPlay: () -> Unit = {},
    lifetimeXp: Int = userStats.xpPoints,
    playedDays: Set<LocalDate> = emptySet(),
    mistakeCount: Int = 0,
    onStartMistakeReview: () -> Unit = {},
    challengeNumber: Long = 1,
    todayResult: DailyResult? = null,
    onShareDaily: () -> Unit = {},
    today: LocalDate = LocalDate.now()
) {
    var timeLeft by remember { mutableStateOf(timeLeftToday(LocalDateTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            timeLeft = timeLeftToday(LocalDateTime.now())
            delay(30_000)
        }
    }

    val userTitle = when {
        userStats.currentLevel >= 40 -> "Universal Sage"
        userStats.currentLevel >= 30 -> "Trivia Oracle"
        userStats.currentLevel >= 20 -> "Grand Master"
        userStats.currentLevel >= 12 -> "Quiz Wizard"
        userStats.currentLevel >= 6 -> "Intellect Pro"
        else -> "Quiz Newbie"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item { Header(userStats, userTitle, lifetimeXp, today, onNavigateToProfile) }

        item {
            DailyCard(
                challengeNumber = challengeNumber,
                played = dailyPlayed,
                result = todayResult,
                timeLeft = timeLeft,
                onPlay = onNavigateToDailyChallenge,
                onShare = onShareDaily
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_play_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                    Text("Quick Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = quickPlaySummary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onQuickPlay,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("quick_play_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Play", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    TextButton(
                        onClick = onNavigateToPracticeSelection,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                            .testTag("quick_play_settings_button")
                    ) {
                        Text("Change", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (mistakeCount > 0) {
            item {
                Surface(
                    onClick = onStartMistakeReview,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_mistakes_card")
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Text(
                            text = "Review your mistakes ($mistakeCount)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        item { WeekRow(playedDays, today) }
    }
}

@Composable
private fun Header(
    userStats: UserStats,
    userTitle: String,
    lifetimeXp: Int,
    today: LocalDate,
    onNavigateToProfile: () -> Unit
) {
    val streak = Scoring.effectiveStreak(userStats, today)
    val maxed = userStats.currentLevel >= Scoring.MAX_LEVEL
    val xpInLevel = lifetimeXp % Scoring.XP_PER_LEVEL
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val initialLetter = (userStats.displayName.firstOrNull() ?: 'U').toString().uppercase()
        Surface(
            onClick = onNavigateToProfile,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Profile and settings" }
                .testTag("profile_avatar")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(initialLetter, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hi, ${userStats.displayName}!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$userTitle · Level ${userStats.currentLevel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { if (maxed) 1f else xpInLevel / Scoring.XP_PER_LEVEL.toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .testTag("xp_bar")
                )
                Text(
                    text = if (maxed) "MAX" else "$xpInLevel/${Scoring.XP_PER_LEVEL} XP",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.testTag("streak_chip")
        ) {
            Text(
                text = "🔥 ${streakLabel(streak)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DailyCard(
    challengeNumber: Long,
    played: Boolean,
    result: DailyResult?,
    timeLeft: String,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_challenge_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DAILY CHALLENGE #$challengeNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (played) "New one in $timeLeft" else "Ends in $timeLeft",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Ultimate World Trivia Mix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(2.dp))
            if (played) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (result != null) "Done · ${result.correct}/${result.answers.size}" else "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("daily_done_text")
                    )
                    OutlinedButton(
                        onClick = onPlay,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("daily_challenge_button")
                    ) { Text("View") }
                    Button(
                        onClick = onShare,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("daily_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            } else {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("daily_challenge_button")
                ) {
                    Text("Play today's challenge", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun WeekRow(playedDays: Set<LocalDate>, today: LocalDate) {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val start = today.minusDays(((today.dayOfWeek.value - first.value + 7) % 7).toLong())
    val days = (0..6).map { start.plusDays(it.toLong()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("week_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "This week",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                val played = d in playedDays
                val name = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$name, ${if (played) "played" else if (d.isAfter(today)) "upcoming" else "not played"}"
                    }
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.Normal
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .then(
                                if (played) Modifier.border(5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                    )
                }
            }
        }
    }
}
