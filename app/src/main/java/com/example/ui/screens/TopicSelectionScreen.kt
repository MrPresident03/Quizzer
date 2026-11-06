package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.data.repository.Categories
import com.example.data.repository.PracticeSettings

private const val ENDLESS_COUNT = 999
private val MIN_TOUCH = 48.dp

private fun labelFor(count: Int) = if (count == ENDLESS_COUNT) "Endless" else "$count questions"

/** Text for the sticky start button, e.g. "Start · 3 topics · 10 questions". */
fun practiceStartLabel(flags: Boolean, topics: List<String>, count: Int): String {
    val what = when {
        flags -> "Flags"
        topics.isEmpty() -> "All topics"
        topics.size == 1 -> topics.single()
        else -> "${topics.size} topics"
    }
    return "Start · $what · ${labelFor(count)}"
}

@Composable
fun TopicSelectionScreen(
    onStartQuiz: (categories: List<String>, difficulty: String, count: Int, format: String, timed: Boolean) -> Unit,
    initial: PracticeSettings = PracticeSettings(),
    onStartFlagQuiz: (mode: String, difficulty: String, count: Int) -> Unit = { _, _, _ -> },
    todayTopic: String = remember { topicOfTheDay() }
) {
    val topics = remember { getTopicsList() }
    val capital = remember { topics.first { it.name == Categories.CAPITAL_CITIES } }
    val gridTopics = remember { topics.filter { it.name != Categories.CAPITAL_CITIES } }

    // Selections survive rotation and process death; `initial` (the last choices) seeds them.
    var topicsCsv by rememberSaveable { mutableStateOf(initial.categories.joinToString(";")) }
    var flagsSelected by rememberSaveable { mutableStateOf(false) }
    var difficulty by rememberSaveable { mutableStateOf(initial.difficulty) }
    var count by rememberSaveable { mutableStateOf(initial.count) }
    var format by rememberSaveable { mutableStateOf(initial.format) }
    var timed by rememberSaveable { mutableStateOf(initial.timed) }
    var flagMode by rememberSaveable { mutableStateOf("Mixed") }
    var flagCount by rememberSaveable { mutableStateOf(10) }

    val selected = if (topicsCsv.isEmpty()) emptyList() else topicsCsv.split(";")
    fun setSelected(list: List<String>) { topicsCsv = list.joinToString(";") }
    fun toggleTopic(name: String) {
        flagsSelected = false
        setSelected(if (name in selected) selected - name else selected + name)
    }

    val capitalOnly = !flagsSelected && selected == listOf(Categories.CAPITAL_CITIES)
    val formatOptions = buildList {
        add("Mixed" to "Mixed")
        add("Multiple Choice" to "Multiple choice")
        if (!capitalOnly) add("True/False" to "True/False")
    }
    val effectiveFormat = if (capitalOnly && format == "True/False") "Mixed" else format
    val activeCount = if (flagsSelected) flagCount else count

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Text(
                text = "Practice",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 4.dp)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        if (flagsSelected) onStartFlagQuiz(flagMode, difficulty, flagCount)
                        else onStartQuiz(selected, difficulty, count, effectiveFormat, timed)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .heightIn(min = 56.dp)
                        .testTag("start_practice_quiz_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = practiceStartLabel(flagsSelected, selected, activeCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("SPECIAL MODES")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TopicTile(
                    name = capital.name.removeSuffix(" Challenge"),
                    icon = capital.icon,
                    selected = !flagsSelected && capital.name in selected,
                    badge = false,
                    tag = "topic_${capital.name}",
                    onToggle = { toggleTopic(capital.name) },
                    modifier = Modifier.weight(1f)
                )
                TopicTile(
                    name = "Flags",
                    icon = Icons.Default.Flag,
                    selected = flagsSelected,
                    badge = false,
                    tag = "special_flags",
                    onToggle = {
                        if (flagsSelected) flagsSelected = false
                        else {
                            flagsSelected = true
                            setSelected(emptyList())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("TOPICS  (tap to select · none = all)")
            gridTopics.chunked(2).forEach { rowTopics ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowTopics.forEach { t ->
                        TopicTile(
                            name = t.name,
                            icon = t.icon,
                            selected = !flagsSelected && t.name in selected,
                            badge = t.name == todayTopic,
                            tag = "topic_${t.name}",
                            onToggle = { toggleTopic(t.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowTopics.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("OPTIONS")

            OptionRow(
                label = "Format",
                group = "format",
                options = if (flagsSelected) listOf(
                    "flag_to_country" to "Flag → Name",
                    "country_to_flag" to "Name → Flag",
                    "Mixed" to "Mixed"
                ) else formatOptions,
                selectedValue = if (flagsSelected) flagMode else effectiveFormat,
                onSelect = { if (flagsSelected) flagMode = it else format = it }
            )
            OptionRow(
                label = "Difficulty",
                group = "difficulty",
                options = listOf("Easy" to "Easy", "Medium" to "Medium", "Hard" to "Hard", "Mixed" to "Mixed"),
                selectedValue = difficulty,
                onSelect = { difficulty = it }
            )
            if (flagsSelected) {
                OptionRow(
                    label = "Questions",
                    group = "count",
                    options = listOf("10" to "10", "20" to "20", "40" to "40"),
                    selectedValue = flagCount.toString(),
                    onSelect = { flagCount = it.toInt() }
                )
            } else {
                OptionRow(
                    label = "Questions",
                    group = "count",
                    options = listOf("10" to "10", "20" to "20", "40" to "40", ENDLESS_COUNT.toString() to "Endless"),
                    selectedValue = count.toString(),
                    onSelect = { count = it.toInt() }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = timed,
                        role = Role.Switch,
                        onValueChange = { timed = it }
                    )
                    .testTag("timer_toggle")
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Timer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${PracticeSettings.QUESTION_SECONDS} seconds per question",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = timed, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TopicTile(
    name: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Boolean,
    tag: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .heightIn(min = 64.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (badge) {
                    Text(
                        text = "★ Today",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    group: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, text) ->
                val isSelected = value == selectedValue
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MIN_TOUCH)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .testTag("opt_${group}_$value")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

data class QuizTopicItem(
    val name: String,
    val icon: ImageVector
)

fun getTopicsList(): List<QuizTopicItem> {
    return listOf(
        QuizTopicItem(Categories.CAPITAL_CITIES, Icons.Default.LocationCity),
        QuizTopicItem("General Knowledge", Icons.Default.MenuBook),
        QuizTopicItem("Science & Nature", Icons.Default.Science),
        QuizTopicItem("History", Icons.Default.History),
        QuizTopicItem("Geography", Icons.Default.Public),
        QuizTopicItem("Sports & Recreation", Icons.Default.SportsBasketball),
        QuizTopicItem("Entertainment", Icons.Default.Movie),
        QuizTopicItem("Literature & Books", Icons.Default.ImportContacts),
        QuizTopicItem("Art & Architecture", Icons.Default.Brush),
        QuizTopicItem("Food & Drink", Icons.Default.Restaurant),
        QuizTopicItem("Technology & Computing", Icons.Default.Computer),
        QuizTopicItem("Mathematics & Logic", Icons.Default.Calculate),
        QuizTopicItem("Pop Culture & Memes", Icons.Default.Mood),
        QuizTopicItem("Language & Linguistics", Icons.Default.Translate),
        QuizTopicItem("Politics & World Leaders", Icons.Default.Gavel),
        QuizTopicItem("Business & Economics", Icons.Default.AttachMoney),
        QuizTopicItem("Health & Medicine", Icons.Default.Favorite),
        QuizTopicItem("Religion & Mythology", Icons.Default.AutoAwesome),
        QuizTopicItem("Animals & Wildlife", Icons.Default.Pets),
        QuizTopicItem("Fashion & Design", Icons.Default.Architecture),
        QuizTopicItem("True Crime & Law", Icons.Default.Fingerprint)
    )
}
