package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import com.example.BuildConfig
import com.example.data.model.UserStats
import com.example.notifications.DailyReminder
import com.example.ui.theme.ClassicPrimary
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MidnightPrimary
import com.example.ui.theme.SunsetPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userStats: UserStats,
    onUpdateToggles: (sound: Boolean, haptic: Boolean, textLarge: Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onClearQuestionHistory: () -> Unit,
    reminderEnabled: Boolean = false,
    onReminderChange: (Boolean) -> Unit = {},
    onUpdateProfile: (name: String, avatarId: String) -> Unit = { _, _ -> },
    onSelectTheme: (themeId: String) -> Unit = {}
) {
    var reminderDenied by remember { mutableStateOf(false) }
    // Android 13+ asks for notification permission the first time the reminder is switched on.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminderDenied = !granted
        if (granted) onReminderChange(true)
    }
    var soundEnabled by remember(userStats.isSoundEffectsEnabled) { mutableStateOf(userStats.isSoundEffectsEnabled) }
    var hapticEnabled by remember(userStats.isHapticFeedbackEnabled) { mutableStateOf(userStats.isHapticFeedbackEnabled) }
    var fontLargeEnabled by remember(userStats.fontSizePreference) { mutableStateOf(userStats.fontSizePreference == "large") }

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile (moved here from the old Profile & Shop tab)
            Text("Profile", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ProfileEditor(userStats = userStats, onSave = onUpdateProfile)

            // Themes are all free now that the XP shop is gone
            Text("Theme", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ThemePicker(activeThemeId = userStats.activeThemeId, onSelectTheme = onSelectTheme)

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Section A: Game configurations
            Text("General Preferences", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            // 1. Sound effects toggle
            PreferenceToggle(
                title = "Sound Effects",
                description = "Play correct / incorrect buzzer alerts inside quizzes",
                icon = Icons.Default.VolumeUp,
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    onUpdateToggles(it, hapticEnabled, fontLargeEnabled)
                },
                tag = "sound_toggle"
            )

            // 2. Haptic FX Toggle
            PreferenceToggle(
                title = "Haptic Vibrations",
                description = "Vibrate on answering or choosing alternatives",
                icon = Icons.Default.Vibration,
                checked = hapticEnabled,
                onCheckedChange = {
                    hapticEnabled = it
                    onUpdateToggles(soundEnabled, it, fontLargeEnabled)
                },
                tag = "haptic_toggle"
            )

            // 3. Font Size Normal / Large
            PreferenceToggle(
                title = "Increase Font Text Size",
                description = "Enable larger, more readable display headings",
                icon = Icons.Default.FormatSize,
                checked = fontLargeEnabled,
                onCheckedChange = {
                    fontLargeEnabled = it
                    onUpdateToggles(soundEnabled, hapticEnabled, it)
                },
                tag = "font_toggle"
            )

            // 4. Daily Challenge reminder
            PreferenceToggle(
                title = "Daily Reminder",
                description = if (reminderDenied) "Notifications are blocked — allow them for Quizzer in system settings"
                else "A nudge at ${formatHour12(DailyReminder.REMINDER_HOUR)} if you haven't played the Daily Challenge",
                icon = Icons.Default.NotificationsActive,
                checked = reminderEnabled,
                onCheckedChange = { enable ->
                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        reminderDenied = false
                        onReminderChange(enable)
                    }
                },
                tag = "reminder_toggle"
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Section B: Storage controls
            Text("System Data & Cleanup", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            // 1. Clear Question History
            SettingsActionRow(
                title = "Recycle Seen Questions",
                description = "Forgets which questions you've already seen, so older questions can come up again.",
                buttonText = "Clear History",
                onClick = { showClearHistoryDialog = true },
                tag = "clear_question_history_button"
            )

            // 2. Full Progress Wipe
            SettingsActionRow(
                title = "Reset All Profile Progress",
                description = "Wipes your XP, level, streak, badges and stats. This can't be undone.",
                buttonText = "Hard Reset",
                onClick = { showResetDialog = true },
                tag = "hard_reset_progress_button"
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Section C: About screen details
            Text("App Identity", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Quizzer", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    Text("Credits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("Trivia questions include material from Open Trivia DB (opentdb.com), licensed CC BY-SA 4.0, and facts from Wikidata (CC0).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // --- CONFIRMATION MODAL DIALOGS ---

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Recycle Seen Questions?") },
            text = { Text("The app normally avoids repeating questions you've seen recently. This clears that memory, so previously seen questions can appear again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearQuestionHistory()
                        showClearHistoryDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_history")
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Confirm Absolute Score Wipe?") },
            text = { Text("This permanently resets your name, XP, level, streak, badges, mistakes list and stats.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetProgress()
                        showResetDialog = false
                    },
                    modifier = Modifier.testTag("confirm_hard_reset")
                ) {
                    Text("hard reset", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** 12-hour clock label for a whole hour: 0 -> "12 am", 12 -> "12 pm", 18 -> "6 pm". */
internal fun formatHour12(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val display = if (h % 12 == 0) 12 else h % 12
    return "$display ${if (h < 12) "am" else "pm"}"
}

@Composable
fun PreferenceToggle(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The whole row is the switch: one TalkBack stop, labelled by its title and description.
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag(tag)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
            ) {
                Text(buttonText, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

private val avatarOptions = listOf(
    "avatar_1" to Icons.Default.Face,
    "avatar_2" to Icons.Default.Psychology,
    "avatar_3" to Icons.Default.Stars,
    "avatar_4" to Icons.Default.SportsEsports
)

@Composable
private fun ProfileEditor(userStats: UserStats, onSave: (name: String, avatarId: String) -> Unit) {
    var name by remember(userStats.displayName) { mutableStateOf(userStats.displayName) }
    var avatar by remember(userStats.avatarId) { mutableStateOf(userStats.avatarId) }
    val changed = name.trim() != userStats.displayName || avatar != userStats.avatarId

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
                avatarOptions.forEach { (id, icon) ->
                    val selected = avatar == id
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("avatar_$id")
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { avatar = id })
                            .semantics { contentDescription = avatarDescription(id) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_nickname_input")
            )
            Button(
                onClick = { onSave(name, avatar) },
                enabled = changed && name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_profile_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (changed) "Save profile" else "Saved", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "avatar_1" -> "Avatar 1", so TalkBack never reads out an internal id. */
internal fun avatarDescription(id: String): String = "Avatar ${id.substringAfterLast('_')}"

private data class ThemeOption(val id: String, val name: String, val color: Color)

private val themeOptions = listOf(
    ThemeOption("midnight_blue", "Midnight", MidnightPrimary),
    ThemeOption("forest_green", "Forest", ForestPrimary),
    ThemeOption("sunset_orange", "Sunset", SunsetPrimary),
    ThemeOption("classic", "Classic", ClassicPrimary)
)

@Composable
private fun ThemePicker(activeThemeId: String, onSelectTheme: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        themeOptions.forEach { option ->
            val selected = option.id == activeThemeId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = option.color,
                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground) else null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("select_theme_${option.id}")
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelectTheme(option.id) })
                        .semantics { contentDescription = "${option.name} theme" }
                ) {
                    if (selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
                Text(option.name, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
