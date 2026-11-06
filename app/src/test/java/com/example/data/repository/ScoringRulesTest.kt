package com.example.data.repository

import com.example.data.model.Question
import com.example.data.model.UserStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScoringRulesTest {

    private val today = LocalDate.of(2026, 11, 6)

    private fun q(i: Int = 0) =
        Question(text = "Q$i?", optionsList = listOf("a", "b"), correctIndex = 0, category = "C", difficulty = "easy", explanation = "")

    private fun summary(type: SessionType, answers: List<Boolean>, completed: Boolean = true) = SessionSummary(
        type = type,
        questions = answers.indices.map(::q),
        answers = answers,
        selectedDifficulty = "Mixed",
        categoryLabel = "C",
        isMixedTopics = false,
        durationSeconds = 10,
        bestSpeedStreak = 0,
        lifelinesUsed = 0,
        completed = completed
    )

    // --- A8: perfect rules ---

    @Test
    fun perfectNeedsFiveQuestionsAndNeverAppliesToEndlessOrReview() {
        assertFalse(summary(SessionType.PRACTICE, listOf(true)).isPerfect)
        assertFalse(summary(SessionType.PRACTICE, List(4) { true }).isPerfect)
        assertTrue(summary(SessionType.PRACTICE, List(5) { true }).isPerfect)
        assertFalse(summary(SessionType.PRACTICE, listOf(true, true, true, true, false)).isPerfect)
        assertTrue(summary(SessionType.DAILY, List(10) { true }).isPerfect)
        assertFalse(summary(SessionType.DAILY, List(6) { true }, completed = false).isPerfect) // quit early
        assertFalse(summary(SessionType.ENDLESS, List(30) { true }).isPerfect)
        assertFalse(summary(SessionType.REVIEW, List(10) { true }).isPerfect)
    }

    @Test
    fun shortEndlessAndReviewRunsDoNotCountTowardProgress() {
        assertFalse(summary(SessionType.ENDLESS, List(4) { true }).countsTowardProgress)
        assertFalse(summary(SessionType.REVIEW, List(2) { false }).countsTowardProgress)
        assertTrue(summary(SessionType.ENDLESS, List(5) { true }).countsTowardProgress)
        assertTrue(summary(SessionType.PRACTICE, listOf(true)).countsTowardProgress)
    }

    // --- A10: the streak that is shown ---

    private fun stats(streak: Int, last: LocalDate?, freezes: Int = 0) =
        UserStats(currentStreak = streak, lastQuizDateStr = last?.toString(), hasStreakFreeze = freezes)

    @Test
    fun effectiveStreakShowsZeroOnceTheStreakIsBroken() {
        assertEquals(5, Scoring.effectiveStreak(stats(5, today), today))
        assertEquals(5, Scoring.effectiveStreak(stats(5, today.minusDays(1)), today))
        assertEquals(0, Scoring.effectiveStreak(stats(5, today.minusDays(2)), today))
        assertEquals(0, Scoring.effectiveStreak(stats(5, today.minusDays(30)), today))
        assertEquals(0, Scoring.effectiveStreak(stats(0, null), today))
    }

    @Test
    fun effectiveStreakAgreesWithFreezeRules() {
        // One missed day is covered by one freeze, two need two.
        assertEquals(5, Scoring.effectiveStreak(stats(5, today.minusDays(2), freezes = 1), today))
        assertEquals(0, Scoring.effectiveStreak(stats(5, today.minusDays(3), freezes = 1), today))
        assertEquals(5, Scoring.effectiveStreak(stats(5, today.minusDays(3), freezes = 2), today))
        // Whatever it shows, the next finished quiz continues (or restarts) the streak the same way.
        for (gap in 0L..6L) for (freezes in 0..2) {
            val s = stats(4, today.minusDays(gap), freezes)
            val shown = Scoring.effectiveStreak(s, today)
            val next = Scoring.nextStreak(4, today.minusDays(gap), today, freezes).streak
            assertEquals("gap=$gap freezes=$freezes", shown != 0, next > 4 || (gap == 0L && next == 4))
        }
    }
}
