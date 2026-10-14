package com.example.data.repository

import com.example.data.model.Question
import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** A question the player got wrong, kept until they answer it correctly. */
@JsonClass(generateAdapter = true)
data class MistakeEntry(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: String,
    val difficulty: String,
    val explanation: String,
    val lastMissedAt: Long,
    val misses: Int = 1
) {
    fun toQuestion() = Question(
        text = text,
        optionsList = options,
        correctIndex = correctIndex,
        category = category,
        difficulty = difficulty,
        explanation = explanation
    )
}

/**
 * "Review your mistakes": every wrong (or timed-out) answer is saved here, and answering the same
 * question correctly later — in any mode — clears it. Review quizzes serve the oldest misses first.
 */
class MistakeBook(entries: List<MistakeEntry> = emptyList(), private val capacity: Int = DEFAULT_CAPACITY) {

    private val byKey = LinkedHashMap<String, MistakeEntry>()

    init {
        entries.forEach { byKey[QuestionPicker.keyOf(it.text)] = it }
        trim()
    }

    val size: Int get() = byKey.size

    fun entries(): List<MistakeEntry> = byKey.values.toList()

    fun contains(question: Question): Boolean = byKey.containsKey(QuestionPicker.keyOf(question))

    fun recordWrong(question: Question, at: Long) {
        if (!QuestionPicker.isPlayable(question)) return
        val key = QuestionPicker.keyOf(question)
        val previous = byKey.remove(key)
        byKey[key] = MistakeEntry(
            text = question.text,
            options = question.optionsList,
            correctIndex = question.correctIndex,
            category = question.category,
            difficulty = question.difficulty,
            explanation = question.explanation,
            lastMissedAt = at,
            misses = (previous?.misses ?: 0) + 1
        )
        trim()
    }

    /** Returns true if the question was in the book and has now been cleared. */
    fun recordRight(question: Question): Boolean = byKey.remove(QuestionPicker.keyOf(question)) != null

    /** Oldest misses first, so things you got wrong a while ago come back before fresh ones. */
    fun reviewSet(count: Int): List<Question> =
        byKey.values.sortedBy { it.lastMissedAt }.take(count.coerceAtLeast(0)).map { it.toQuestion() }

    private fun trim() {
        while (byKey.size > capacity) byKey.remove(byKey.keys.first())
    }

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}

/** Per-topic totals for the "Topic mastery" panel. */
data class TopicStat(val topic: String, val answered: Int, val correct: Int) {
    val accuracy: Float get() = if (answered == 0) 0f else correct.toFloat() / answered
}

/** Groups the two flag directions under one "Flags" topic for stats. */
fun statsTopicFor(category: String): String = if (category.startsWith("Flags")) "Flags" else category

/** Today's daily-challenge result, kept so it can be shown and shared later in the day. */
data class DailyResult(val date: String, val answers: List<Boolean>, val seconds: Int) {
    val correct: Int get() = answers.count { it }

    fun serialize(): String = "$date|${answers.joinToString("") { if (it) "1" else "0" }}|$seconds"

    companion object {
        fun parse(value: String?): DailyResult? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 3) return null
            val seconds = parts[2].toIntOrNull() ?: return null
            if (parts[1].any { it != '0' && it != '1' }) return null
            return DailyResult(parts[0], parts[1].map { it == '1' }, seconds)
        }
    }
}

/**
 * The Daily Challenge is built only from the bundled question bank plus generated flag/capital
 * questions, seeded by the date — so every player gets the same 10 questions on the same day and
 * results can be compared and shared (Wordle-style). It works fully offline.
 */
object DailyChallenge {
    private val FIRST_DAY: LocalDate = LocalDate.of(2026, 1, 1)
    const val TRIVIA_PER_DAY = 6
    private const val FLAGS_PER_DAY = 2
    private const val CAPITALS_PER_DAY = 2

    /** Challenge number shown when sharing, e.g. "Daily #287". */
    fun number(date: LocalDate): Long = ChronoUnit.DAYS.between(FIRST_DAY, date) + 1

    fun build(bundled: List<Question>, date: LocalDate): List<Question> {
        val bank = bundled.filter(QuestionPicker::isPlayable).distinctBy { QuestionPicker.keyOf(it) }.sortedBy { it.text }
        val epochDay = date.toEpochDay()
        val random = Random(epochDay * 1_000_003L + 7)

        // Walk through a seeded permutation of the bank, so no trivia question repeats until the
        // whole bank has been used (about two months with the current bank).
        val trivia = if (bank.size >= TRIVIA_PER_DAY) {
            val daysPerCycle = bank.size / TRIVIA_PER_DAY
            val cycle = Math.floorDiv(epochDay, daysPerCycle.toLong())
            val slot = Math.floorMod(epochDay, daysPerCycle.toLong()).toInt()
            val permutation = bank.shuffled(Random(cycle * 7_919L + 13))
            permutation.subList(slot * TRIVIA_PER_DAY, slot * TRIVIA_PER_DAY + TRIVIA_PER_DAY)
        } else bank

        val flags = FlagQuestionsProvider.generateFlagQuiz("Mixed", "Mixed", FLAGS_PER_DAY, random = random)
        val capitals = CapitalCitiesProvider.generateCapitalQuiz("Mixed", CAPITALS_PER_DAY, random = random)

        // Easy -> hard so the challenge ramps up.
        return (trivia + flags + capitals)
            .map { QuestionPicker.shuffleOptions(it, random) }
            .sortedBy { difficultyRank(it.difficulty) }
    }

    fun shareText(date: LocalDate, result: DailyResult): String {
        val minutes = result.seconds / 60
        val seconds = result.seconds % 60
        val squares = result.answers.joinToString("") { if (it) "🟩" else "🟥" }
        return buildString {
            append("🧠 BrainBlitz Daily #").append(number(date)).append('\n')
            append(result.correct).append('/').append(result.answers.size)
            append("  ⏱ ").append(minutes).append(':').append(seconds.toString().padStart(2, '0')).append('\n')
            append(squares)
        }
    }

    private fun difficultyRank(difficulty: String) = when (difficulty.lowercase()) {
        "easy" -> 0
        "medium" -> 1
        else -> 2
    }
}
