package com.example.data.repository

import com.example.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LearningToolsTest {

    private fun q(text: String, difficulty: String = "easy") = Question(
        text = text, optionsList = listOf("A", "B", "C", "D"), correctIndex = 2,
        category = "History", difficulty = difficulty, explanation = "because"
    )

    private val bank = (1..120).map { q("Bundled question $it?", listOf("easy", "medium", "hard")[it % 3]) }

    @Test
    fun mistakesAreAddedClearedAndReviewedOldestFirst() {
        val book = MistakeBook()
        book.recordWrong(q("First?"), at = 100)
        book.recordWrong(q("Second?"), at = 200)
        book.recordWrong(q("First?"), at = 300) // missed again: moves to newest, miss count goes up
        assertEquals(2, book.size)
        assertEquals(listOf("Second?", "First?"), book.reviewSet(10).map { it.text })
        assertEquals(2, book.entries().single { it.text == "First?" }.misses)

        assertTrue(book.recordRight(q("second?"))) // same question, different case
        assertFalse(book.recordRight(q("Never missed?")))
        assertEquals(1, book.size)
        assertEquals(2, book.reviewSet(10).single().correctIndex)
    }

    @Test
    fun mistakeBookIsCapped() {
        val book = MistakeBook(capacity = 3)
        (1..5).forEach { book.recordWrong(q("Q$it?"), at = it.toLong()) }
        assertEquals(listOf("Q3?", "Q4?", "Q5?"), book.reviewSet(10).map { it.text })
    }

    @Test
    fun dailyResultRoundTrips() {
        val result = DailyResult("2026-10-14", listOf(true, false, true), 83)
        assertEquals(result, DailyResult.parse(result.serialize()))
        assertNull(DailyResult.parse("garbage"))
        assertNull(DailyResult.parse("2026-10-14|10x|5"))
    }

    @Test
    fun dailyChallengeIsTheSameForEveryoneOnTheSameDay() {
        val day = LocalDate.of(2026, 10, 14)
        val first = DailyChallenge.build(bank, day)
        val second = DailyChallenge.build(bank.shuffled(), day) // DB order must not matter
        assertEquals(10, first.size)
        assertEquals(first, second)
        assertNotEquals(first, DailyChallenge.build(bank, day.plusDays(1)))
    }

    @Test
    fun dailyTriviaDoesNotRepeatWithinACycle() {
        val daysPerCycle = bank.size / DailyChallenge.TRIVIA_PER_DAY
        val start = LocalDate.of(2026, 1, 1)
        // Align to the start of a cycle so the whole window is one permutation.
        val cycleStart = start.plusDays(Math.floorMod(-start.toEpochDay(), daysPerCycle.toLong()))
        val trivia = (0 until daysPerCycle).flatMap { offset ->
            DailyChallenge.build(bank, cycleStart.plusDays(offset.toLong()))
                .filter { it.text.startsWith("Bundled") }
                .map { it.text }
        }
        assertEquals(bank.size, trivia.size)
        assertEquals(trivia.size, trivia.toSet().size)
    }

    @Test
    fun dailyRampsFromEasyToHard() {
        val ranks = DailyChallenge.build(bank, LocalDate.of(2026, 3, 3)).map {
            when (it.difficulty) { "easy" -> 0; "medium" -> 1; else -> 2 }
        }
        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun shareTextLooksLikeWordle() {
        val text = DailyChallenge.shareText(
            LocalDate.of(2026, 10, 14),
            DailyResult("2026-10-14", listOf(true, true, false, true), 105)
        )
        assertEquals("🧠 BrainBlitz Daily #287\n3/4  ⏱ 1:45\n🟩🟩🟥🟩", text)
    }

    @Test
    fun flagsAreGroupedForStats() {
        assertEquals("Flags", statsTopicFor("Flags: Country to Flag"))
        assertEquals("History", statsTopicFor("History"))
        assertEquals(0.5f, TopicStat("History", 4, 2).accuracy, 0.001f)
    }
}
