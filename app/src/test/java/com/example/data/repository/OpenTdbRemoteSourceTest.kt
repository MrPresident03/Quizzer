package com.example.data.repository

import com.example.data.api.OpenTdbResponse
import com.example.data.api.OpenTdbResult
import com.example.data.api.OpenTdbService
import com.example.data.api.OpenTdbTokenResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTdbRemoteSourceTest {

    private class FakeTokenStore : OpenTdbTokenStore {
        override var openTdbToken: String? = null
    }

    private data class Call(val amount: Int, val category: Int?, val difficulty: String?, val type: String?, val token: String?)

    /** Replies with the scripted response codes in order; code 0 returns [amount] questions. */
    private class FakeService(private val codes: MutableList<Int>) : OpenTdbService {
        val calls = mutableListOf<Call>()
        var tokensRequested = 0
        var tokensReset = 0

        override suspend fun getQuestions(amount: Int, category: Int?, difficulty: String?, type: String?, token: String?): OpenTdbResponse {
            calls += Call(amount, category, difficulty, type, token)
            val code = if (codes.isEmpty()) 0 else codes.removeAt(0)
            val results = if (code != 0) emptyList() else (1..amount).map {
                OpenTdbResult(
                    category = "Entertainment: Film",
                    type = "multiple",
                    difficulty = "Medium",
                    question = "Question $it?",
                    correctAnswer = "Right",
                    incorrectAnswers = listOf("W1", "W2", "W3")
                )
            }
            return OpenTdbResponse(code, results)
        }

        override suspend fun requestToken(): OpenTdbTokenResponse {
            tokensRequested++
            return OpenTdbTokenResponse(0, "token$tokensRequested")
        }

        override suspend fun resetToken(token: String): OpenTdbTokenResponse {
            tokensReset++
            return OpenTdbTokenResponse(0, token)
        }
    }

    private fun source(service: FakeService, store: OpenTdbTokenStore = FakeTokenStore()) =
        OpenTdbRemoteSource(store, service, decode = { it }, minCallSpacingMs = 0)

    @Test
    fun usesAndPersistsASessionToken() = runBlocking {
        val store = FakeTokenStore()
        val service = FakeService(mutableListOf(0, 0))
        val remote = source(service, store)
        remote.fetch(null, null, null, 5)
        remote.fetch(null, null, null, 5)
        assertEquals(1, service.tokensRequested)
        assertEquals("token1", store.openTdbToken)
        assertTrue(service.calls.all { it.token == "token1" })
    }

    @Test
    fun retriesAfterRateLimitInsteadOfGivingUp() = runBlocking {
        val service = FakeService(mutableListOf(5, 0))
        val result = source(service).fetch("History", "easy", null, 10)
        assertEquals(10, result.size)
        assertEquals(2, service.calls.size)
    }

    @Test
    fun notEnoughQuestionsShrinksTheRequestThenDropsDifficulty() = runBlocking {
        val service = FakeService(mutableListOf(1, 1, 1, 0))
        val result = source(service).fetch("History", "hard", null, 20)
        assertEquals(listOf(20, 10, 5, 5), service.calls.map { it.amount })
        assertEquals(listOf("hard", "hard", "hard", null), service.calls.map { it.difficulty })
        assertEquals(5, result.size)
    }

    @Test
    fun exhaustedTokenIsResetAndMissingTokenReplaced() = runBlocking {
        val store = FakeTokenStore()
        val service = FakeService(mutableListOf(4, 3, 0))
        val result = source(service, store).fetch(null, null, null, 5)
        assertEquals(5, result.size)
        assertEquals(1, service.tokensReset)
        assertEquals(2, service.tokensRequested)
    }

    @Test
    fun offlineOnlyTopicsNeverHitTheApi() = runBlocking {
        val service = FakeService(mutableListOf())
        assertTrue(source(service).fetch("Food & Drink", null, null, 10).isEmpty())
        assertTrue(service.calls.isEmpty())
    }

    @Test
    fun mapsResultsOntoAppTopicsWithAValidAnswer() = runBlocking {
        val service = FakeService(mutableListOf(0))
        val anyTopic = source(service).fetch(null, null, null, 3)
        anyTopic.forEach {
            assertEquals("Entertainment", it.category)
            assertEquals("Right", it.optionsList[it.correctIndex])
            assertEquals("medium", it.difficulty)
        }
        assertNull(service.calls.single().category)
    }
}
