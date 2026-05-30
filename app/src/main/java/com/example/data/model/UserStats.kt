package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 1, // Only 1 local user profile
    val displayName: String = "Quiz Champ",
    val avatarId: String = "avatar_1",
    val currentStreak: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val correctAnswersCount: Int = 0,
    val xpPoints: Int = 0,
    val currentLevel: Int = 1,
    val lastQuizDateStr: String? = null, // YYYY-MM-DD
    val hasStreakFreeze: Int = 0, // Number of streak freeze items owned
    
    // Lifelines (purchased/earned via XP)
    val lifelineFiftyFiftyCount: Int = 3,
    val lifelineSkipCount: Int = 3,
    val lifelineExtraTimeCount: Int = 3,
    val lifelineAskAudienceCount: Int = 3,
    
    // Customization & Settings
    val activeThemeId: String = "midnight_blue", // "midnight_blue", "forest_green", "sunset_orange", "classic"
    val unlockedThemesJson: String = "[\"midnight_blue\", \"classic\"]", // List of unlocked themes as JSON
    val fontSizePreference: String = "normal", // "normal", "large"
    val isSoundEffectsEnabled: Boolean = true,
    val isHapticFeedbackEnabled: Boolean = true,
    val isDailyNotificationEnabled: Boolean = true,
    val dailyNotificationTimeStr: String = "08:00"
) {
    val accuracyPercentage: Float
        get() = if (totalQuestionsAnswered == 0) 0f else (correctAnswersCount.toFloat() / totalQuestionsAnswered * 100)
}
