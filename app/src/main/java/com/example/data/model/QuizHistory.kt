package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_history")
data class QuizHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val categoryName: String,
    val score: Int,
    val totalQuestions: Int,
    val difficulty: String,
    val timeTakenSeconds: Int,
    val xpEarned: Int,
    val quizType: String // "Practice" or "Daily"
) {
    val accuracy: Float
        get() = if (totalQuestions == 0) 0f else (score.toFloat() / totalQuestions * 100)
}
