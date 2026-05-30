package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val optionsList: List<String>,
    val correctIndex: Int,
    val category: String,
    val difficulty: String, // "easy", "medium", "hard"
    val explanation: String,
    val imageUrl: String? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0
)
