package com.example.data.repository

import com.example.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ScoringAndProvidersTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 14)

    private fun q(difficulty: String) =
        Question(text = "q", optionsList = listOf("a", "b"), correctIndex = 0, category = "c", difficulty = difficulty, explanation = "")

    @Test
    fun streakRules() {
        assertEquals(StreakResult(1, 0), Scoring.nextStreak(0, null, today, 0))
        assertEquals(StreakResult(4, 0), Scoring.nextStreak(4, today, today, 0))
        assertEquals(StreakResult(5, 0), Scoring.nextStreak(4, today.minusDays(1), today, 0))
        assertEquals(StreakResult(1, 0), Scoring.nextStreak(4, today.minusDays(2), today, 0))
        // One missed day covered by one freeze, which is actually consumed.
        assertEquals(StreakResult(5, 1), Scoring.nextStreak(4, today.minusDays(2), today, 3))
        // Two missed days need two freezes.
        assertEquals(StreakResult(1, 0), Scoring.nextStreak(4, today.minusDays(3), today, 1))
        assertEquals(StreakResult(5, 2), Scoring.nextStreak(4, today.minusDays(3), today, 2))
    }

    @Test
    fun xpWeightsEachQuestionsDifficultyAndOnlyRewardsRealPerfects() {
        val questions = listOf(q("easy"), q("medium"), q("hard"))
        assertEquals(10 + 12 + 15 + Scoring.PERFECT_BONUS, Scoring.sessionXp(questions, listOf(true, true, true)))
        assertEquals(12, Scoring.sessionXp(questions, listOf(false, true, false)))
        assertEquals(0, Scoring.sessionXp(emptyList(), emptyList())) // no free "perfect" bonus for an empty quiz
    }

    @Test
    fun levelComesFromLifetimeXp() {
        assertEquals(1, Scoring.levelForLifetimeXp(0))
        assertEquals(2, Scoring.levelForLifetimeXp(350))
        assertEquals(Scoring.MAX_LEVEL, Scoring.levelForLifetimeXp(1_000_000))
    }

    @Test
    fun flagQuizCyclesThroughCountriesBeforeRepeating() {
        val seen = HashMap<String, Long>()
        val asked = mutableListOf<String>()
        var clock = 0L
        val easyCount = FlagQuestionsProvider.countries.count { it.difficulty == "easy" }

        // Play easy flag quizzes of 10 until we've covered the whole easy pool.
        repeat(easyCount / 10) {
            val quiz = FlagQuestionsProvider.generateFlagQuiz("flag_to_country", "easy", 10, { seen[it] }, Random(it))
            quiz.forEach { question ->
                assertTrue(question.correctIndex in question.optionsList.indices)
                assertEquals(4, question.optionsList.distinct().size)
                asked += question.optionsList[question.correctIndex]
                seen[QuestionPicker.keyOf(question)] = ++clock
            }
        }
        assertEquals("no country should repeat until the pool is used up", asked.size, asked.toSet().size)
    }

    @Test
    fun capitalQuizNeverAsksWhichCountryHasItsOwnNameAsCapital() {
        repeat(20) { seed ->
            CapitalCitiesProvider.generateCapitalQuiz("hard", 40, random = Random(seed)).forEach { question ->
                assertTrue(question.correctIndex in question.optionsList.indices)
                val giveaway = listOf("San Marino", "Monaco", "Luxembourg").any {
                    question.text == "Which country has $it as its official capital city?"
                }
                assertTrue("giveaway question: ${question.text}", !giveaway)
            }
        }
    }

    @Test
    fun categoryMappingRoundTrips() {
        assertEquals("Entertainment", Categories.appTopicForOpenTdbLabel("Entertainment: Video Games"))
        assertEquals("Literature & Books", Categories.appTopicForOpenTdbLabel("Entertainment: Books"))
        assertEquals("Technology & Computing", Categories.appTopicForOpenTdbLabel("Science: Computers"))
        assertTrue(Categories.openTdbIdsFor("Food & Drink").isEmpty()) // offline-only topic, never "any category"
    }
}
