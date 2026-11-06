package com.example.data.repository

import com.example.data.model.Question
import com.example.data.model.UserStats
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** What kind of session is being played. Passed explicitly instead of being guessed afterwards. */
enum class SessionType(val label: String) {
    PRACTICE("Practice"),
    DAILY("Daily"),
    FLAGS("Flags"),
    ENDLESS("Endless"),
    REVIEW("Review")
}

/** Everything the repository needs to record a finished session and evaluate achievements. */
data class SessionSummary(
    val type: SessionType,
    /** Only the questions that were actually played (endless mode prefetches extras). */
    val questions: List<Question>,
    val answers: List<Boolean>,
    /** The difficulty the player chose ("easy", "medium", "hard" or "Mixed"). */
    val selectedDifficulty: String,
    val categoryLabel: String,
    val isMixedTopics: Boolean,
    val durationSeconds: Int,
    val bestSpeedStreak: Int,
    val lifelinesUsed: Int,
    /** The Daily Challenge's date as captured when the quiz started (a quiz can finish after midnight). */
    val dailyDate: LocalDate? = null,
    /** False when the player left early (e.g. quitting the Daily part-way): never a "perfect" session. */
    val completed: Boolean = true
) {
    val correctCount: Int get() = answers.count { it }
    val totalCount: Int get() = questions.size
    val isPerfect: Boolean
        get() = completed && Scoring.isPerfect(type, totalCount, answers)

    /**
     * Endless and Review runs of fewer than [Scoring.MIN_COUNTED_ANSWERS] answers are too easy to
     * start and stop on purpose, so they don't count towards quiz-count achievements or the streak.
     */
    val countsTowardProgress: Boolean
        get() = !((type == SessionType.ENDLESS || type == SessionType.REVIEW) && answers.size < Scoring.MIN_COUNTED_ANSWERS)
}

data class StreakResult(val streak: Int, val freezesUsed: Int)

/** Pure scoring rules shared by the repository (what gets saved) and the results screen (what is shown). */
object Scoring {
    const val XP_PER_CORRECT = 10
    const val PERFECT_BONUS = 50
    const val XP_PER_LEVEL = 350
    const val MAX_LEVEL = 50

    /** A "perfect" session needs at least this many questions (1/1 is not a perfect quiz). */
    const val MIN_PERFECT_QUESTIONS = 5
    const val MIN_COUNTED_ANSWERS = 5

    /** Never applies to Endless or Review, and needs [MIN_PERFECT_QUESTIONS] questions, all answered right. */
    fun isPerfect(type: SessionType, totalCount: Int, answers: List<Boolean>): Boolean =
        type != SessionType.ENDLESS && type != SessionType.REVIEW &&
            totalCount >= MIN_PERFECT_QUESTIONS && answers.size == totalCount && answers.all { it }

    fun difficultyMultiplier(difficulty: String): Float = when (difficulty.lowercase()) {
        "hard" -> 1.5f
        "medium" -> 1.2f
        else -> 1.0f
    }

    /** Each correct answer is weighted by that question's own difficulty, plus a flat perfect-score bonus. */
    fun sessionXp(questions: List<Question>, answers: List<Boolean>, type: SessionType = SessionType.PRACTICE): Int {
        var xp = 0f
        answers.forEachIndexed { index, correct ->
            if (correct) {
                val difficulty = questions.getOrNull(index)?.difficulty ?: "easy"
                xp += XP_PER_CORRECT * difficultyMultiplier(difficulty)
            }
        }
        if (isPerfect(type, questions.size, answers)) xp += PERFECT_BONUS
        return xp.roundToInt()
    }

    /** Level is based on lifetime XP earned, so spending XP in the shop never drops your level. */
    fun levelForLifetimeXp(lifetimeXp: Int): Int =
        (lifetimeXp.coerceAtLeast(0) / XP_PER_LEVEL + 1).coerceAtMost(MAX_LEVEL)

    /**
     * Daily streak rules: playing again the same day keeps the streak, playing the next day extends it,
     * and each owned streak freeze can cover one missed day.
     */
    fun nextStreak(currentStreak: Int, lastPlayed: LocalDate?, today: LocalDate, freezesOwned: Int): StreakResult {
        if (lastPlayed == null) return StreakResult(1, 0)
        val gap = ChronoUnit.DAYS.between(lastPlayed, today)
        val missedDays = gap - 1
        return when {
            gap <= 0L -> StreakResult(currentStreak.coerceAtLeast(1), 0)
            gap == 1L -> StreakResult(currentStreak + 1, 0)
            missedDays <= freezesOwned -> StreakResult(currentStreak + 1, missedDays.toInt())
            else -> StreakResult(1, 0)
        }
    }

    /**
     * The streak to show right now. [UserStats.currentStreak] is only updated when a quiz is finished,
     * so after missed days it would still show the old number. Uses the same freeze rules as
     * [nextStreak]: today or yesterday keeps it, a gap covered by owned freezes keeps it, a bigger gap is 0.
     */
    fun effectiveStreak(stats: UserStats, today: LocalDate): Int {
        val lastPlayed = parseDate(stats.lastQuizDateStr) ?: return 0
        val missedDays = ChronoUnit.DAYS.between(lastPlayed, today) - 1
        return if (missedDays <= stats.hasStreakFreeze) stats.currentStreak.coerceAtLeast(0) else 0
    }

    fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
