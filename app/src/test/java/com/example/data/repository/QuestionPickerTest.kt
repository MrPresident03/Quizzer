package com.example.data.repository

import com.example.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionPickerTest {

    private fun q(text: String, options: List<String> = listOf("A", "B", "C", "D"), correct: Int = 1, category: String = "History") =
        Question(text = text, optionsList = options, correctIndex = correct, category = category, difficulty = "easy", explanation = "")

    @Test
    fun keyIgnoresCaseAndWhitespace() {
        assertEquals(QuestionPicker.keyOf("What is  the capital?"), QuestionPicker.keyOf(" what is the\ncapital? "))
        assertNotEquals(QuestionPicker.keyOf("Question one"), QuestionPicker.keyOf("Question two"))
    }

    @Test
    fun shuffleOptionsKeepsTheCorrectAnswerAndMovesIt() {
        val original = q("Who painted the Mona Lisa?", listOf("Van Gogh", "Da Vinci", "Picasso", "Monet"), correct = 1)
        val random = Random(42)
        val positions = mutableSetOf<Int>()
        repeat(200) {
            val shuffled = QuestionPicker.shuffleOptions(original, random)
            assertEquals("Da Vinci", shuffled.optionsList[shuffled.correctIndex])
            assertEquals(original.optionsList.toSet(), shuffled.optionsList.toSet())
            positions += shuffled.correctIndex
        }
        assertEquals("correct answer should land in every slot over many shuffles", setOf(0, 1, 2, 3), positions)
    }

    @Test
    fun trueFalseIsNormalisedNotShuffled() {
        val tf = q("The sun is a star.", listOf("False", "True"), correct = 1)
        val result = QuestionPicker.shuffleOptions(tf, Random(1))
        assertEquals(listOf("True", "False"), result.optionsList)
        assertEquals(0, result.correctIndex)
    }

    @Test
    fun pickFreshPrefersUnseenThenOldest() {
        val items = listOf("a", "b", "c", "d", "e")
        val lastSeen = mapOf("a" to 300L, "b" to 100L, "c" to 200L) // d, e never seen
        val picked = QuestionPicker.pickFresh(items, 4, { lastSeen[it] }, Random(7))
        assertEquals(setOf("d", "e"), picked.take(2).toSet())
        assertEquals(listOf("b", "c"), picked.drop(2))
    }

    @Test
    fun pickFreshVariesBetweenCallsWhenNothingIsSeen() {
        val items = (1..100).map { "q$it" }
        val first = QuestionPicker.pickFresh(items, 10, { null })
        val second = QuestionPicker.pickFresh(items, 10, { null })
        assertNotEquals(first, second)
    }

    @Test
    fun balancedPickMixesTopics() {
        val history = (1..20).map { "h$it" }
        val science = (1..20).map { "s$it" }
        val food = listOf("f1", "f2")
        val picked = QuestionPicker.balancedPick(listOf(history, science, food), 10, Random(3))
        assertEquals(10, picked.size)
        assertEquals(2, picked.count { it.startsWith("f") })
        assertEquals(4, picked.count { it.startsWith("h") })
        assertEquals(4, picked.count { it.startsWith("s") })
    }

    @Test
    fun balancedPickReturnsWhatItCanWhenShort() {
        assertEquals(3, QuestionPicker.balancedPick(listOf(listOf(1, 2), listOf(3)), 10).size)
        assertTrue(QuestionPicker.balancedPick(emptyList<List<Int>>(), 5).isEmpty())
    }

    @Test
    fun dedupeAndPlayability() {
        val list = listOf(q("Same text"), q("same  TEXT"), q("Other"))
        assertEquals(2, QuestionPicker.dedupe(list).size)
        assertFalse(QuestionPicker.isPlayable(q("Bad index", correct = 9)))
        assertFalse(QuestionPicker.isPlayable(q("One option", options = listOf("Only"), correct = 0)))
        assertTrue(QuestionPicker.isPlayable(q("Fine")))
    }

    @Test
    fun seenLogEvictsOldestAndRoundTrips() {
        val log = SeenLog(capacity = 3)
        log.mark("a", 1)
        log.mark("b", 2)
        log.mark("c", 3)
        log.mark("a", 4) // refresh "a"
        log.mark("d", 5) // evicts "b", the least recently marked
        assertNull(log.lastSeen("b"))
        assertEquals(4L, log.lastSeen("a"))

        val restored = SeenLog.parse(log.serialize(), capacity = 3)
        assertEquals(log.snapshot(), restored.snapshot())
        assertEquals(0, SeenLog.parse("garbage\n\t\n12\t").size)
    }
}
