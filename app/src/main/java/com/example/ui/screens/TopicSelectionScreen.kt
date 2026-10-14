package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.PracticeSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSelectionScreen(
    onNavigateBack: () -> Unit,
    onStartQuiz: (categories: List<String>, difficulty: String, count: Int, format: String, timed: Boolean) -> Unit,
    initial: PracticeSettings = PracticeSettings(),
    onStartFlagQuiz: (mode: String, difficulty: String, count: Int) -> Unit = { _, _, _ -> }
) {
    val topics = remember { getTopicsList() }
    // Start from the last settings you played with (the same ones Quick Play uses).
    val selectedTopics = remember { mutableStateListOf<String>().apply { addAll(initial.categories) } }

    var selectedDifficulty by remember { mutableStateOf(initial.difficulty) }
    var selectedCount by remember { mutableStateOf(initial.count) }
    var selectedFormat by remember { mutableStateOf(initial.format) }
    var isTimedEnabled by remember { mutableStateOf(initial.timed) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Quiz Suite", fontWeight = FontWeight.ExtraBold) },
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
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Description
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Select Topics to Study",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select one or more categories below. Leaving none selected practices ALL topics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // Featured Capital Cities Challenge Card
            Card(
                onClick = {
                    onStartQuiz(listOf("Capital Cities Challenge"), selectedDifficulty, selectedCount, selectedFormat, isTimedEnabled)
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("featured_capital_cities_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = "Capital Cities Only",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Capital Cities Only Challenge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Test your knowledge exclusively on capitals of sovereign countries across all continents!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(
                        onClick = {
                            onStartQuiz(listOf("Capital Cities Challenge"), selectedDifficulty, selectedCount, selectedFormat, isTimedEnabled)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start capitals only challenge",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Flag quiz (moved here from the home screen)
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                FlagQuizCard(onStartFlagQuiz = onStartFlagQuiz)
            }

            // Topic selection Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 20.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(topics) { topic ->
                        val isSelected = selectedTopics.contains(topic.name)
                        Card(
                            onClick = {
                                if (isSelected) {
                                    selectedTopics.remove(topic.name)
                                } else {
                                    selectedTopics.add(topic.name)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) topic.color.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(2.dp, topic.color)
                            } else null,
                            modifier = Modifier.height(68.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(topic.color.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = topic.icon,
                                        contentDescription = topic.name,
                                        tint = topic.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = topic.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(horizontal = 20.dp))

            // Sub-Settings Panels
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Difficulty
                Text("Select Difficulty", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Mixed", "Easy", "Medium", "Hard").forEach { diff ->
                        val isSelected = selectedDifficulty == diff
                        Button(
                            onClick = { selectedDifficulty = diff },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(diff, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Length (Question Count)
                Text("Number of Questions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "10 Qs" to 10,
                        "20 Qs" to 20,
                        "30 Qs" to 30,
                        "Endless" to 999
                    ).forEach { (label, countValue) ->
                        val isSelected = selectedCount == countValue
                        Button(
                            onClick = { selectedCount = countValue },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Question format
                Text("Question Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Mixed", "Multiple Choice", "True/False").forEach { fmt ->
                        val isSelected = selectedFormat == fmt
                        Button(
                            onClick = { selectedFormat = fmt },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(fmt, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. Timer Toggle option
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.HourglassEmpty, contentDescription = "Timer Settings")
                            Column {
                                Text("Timed Questions Enabled", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${PracticeSettings.QUESTION_SECONDS} seconds per question", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        Switch(
                            checked = isTimedEnabled,
                            onCheckedChange = { isTimedEnabled = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Start Practice Session Master Button
                Button(
                    onClick = {
                        val activeCats = selectedTopics.toList()
                        onStartQuiz(activeCats, selectedDifficulty, selectedCount, selectedFormat, isTimedEnabled)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_practice_quiz_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start Quiz")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Begin Practice Blitz",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

data class QuizTopicItem(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

fun getTopicsList(): List<QuizTopicItem> {
    return listOf(
        QuizTopicItem("Capital Cities Challenge", Icons.Default.LocationCity, Color(0xFF00C853)),
        QuizTopicItem("General Knowledge", Icons.Default.MenuBook, Color(0xFFF57C00)),
        QuizTopicItem("Science & Nature", Icons.Default.Science, Color(0xFF388E3C)),
        QuizTopicItem("History", Icons.Default.History, Color(0xFF8D6E63)),
        QuizTopicItem("Geography", Icons.Default.Public, Color(0xFF1976D2)),
        QuizTopicItem("Sports & Recreation", Icons.Default.SportsBasketball, Color(0xFFE65100)),
        QuizTopicItem("Entertainment", Icons.Default.Movie, Color(0xFFC2185B)),
        QuizTopicItem("Literature & Books", Icons.Default.ImportContacts, Color(0xFF303F9F)),
        QuizTopicItem("Art & Architecture", Icons.Default.Brush, Color(0xFFD32F2F)),
        QuizTopicItem("Food & Drink", Icons.Default.Restaurant, Color(0xFFFBC02D)),
        QuizTopicItem("Technology & Computing", Icons.Default.Computer, Color(0xFF0097A7)),
        QuizTopicItem("Mathematics & Logic", Icons.Default.Calculate, Color(0xFF7B1FA2)),
        QuizTopicItem("Pop Culture & Memes", Icons.Default.Mood, Color(0xFF00796B)),
        QuizTopicItem("Language & Linguistics", Icons.Default.Translate, Color(0xFF5E35B1)),
        QuizTopicItem("Politics & World Leaders", Icons.Default.Gavel, Color(0xFF455A64)),
        QuizTopicItem("Business & Economics", Icons.Default.AttachMoney, Color(0xFF2E7D32)),
        QuizTopicItem("Health & Medicine", Icons.Default.Favorite, Color(0xFFC62828)),
        QuizTopicItem("Religion & Mythology", Icons.Default.AutoAwesome, Color(0xFFE6AF2E)),
        QuizTopicItem("Animals & Wildlife", Icons.Default.Pets, Color(0xFF558B2F)),
        QuizTopicItem("Fashion & Design", Icons.Default.Architecture, Color(0xFFAD1457)),
        QuizTopicItem("True Crime & Law", Icons.Default.Fingerprint, Color(0xFF37474F))
    )
}
