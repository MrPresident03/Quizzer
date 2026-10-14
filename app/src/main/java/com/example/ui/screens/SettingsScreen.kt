package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userStats: UserStats,
    onUpdateToggles: (sound: Boolean, haptic: Boolean, textLarge: Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onClearQuestionHistory: () -> Unit
) {
    var soundEnabled by remember(userStats.isSoundEffectsEnabled) { mutableStateOf(userStats.isSoundEffectsEnabled) }
    var hapticEnabled by remember(userStats.isHapticFeedbackEnabled) { mutableStateOf(userStats.isHapticFeedbackEnabled) }
    var fontLargeEnabled by remember(userStats.fontSizePreference) { mutableStateOf(userStats.fontSizePreference == "large") }

    var showResetDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                description = "Wipes all coins, levels, active streak counts, achievements lists, and gameplay stats forever.",
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
                    Text("BrainBlitz Suite", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
                    Text("Version: 2.0.4 (Production Alpha)", style = MaterialTheme.typography.bodySmall)
                    Text("Built securely in secure Kotlin using modern Jetpack Compose M3 and local Room seeder engines under Clean MVVM conventions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("© 2026 BrainBlitz. All intellectual trademarks preserved.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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
            text = { Text("This is IRREVERSIBLE. This will reset display names, delete coins, current level progress, and wipe your historic analytics back to zero!") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag)
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
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.testTag(tag)
            ) {
                Text(buttonText, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
