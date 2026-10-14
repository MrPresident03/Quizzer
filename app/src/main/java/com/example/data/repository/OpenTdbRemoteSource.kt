package com.example.data.repository

import android.util.Log
import com.example.data.api.OpenTdbApiClient
import com.example.data.api.OpenTdbResult
import com.example.data.api.OpenTdbService
import com.example.data.model.Question
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/**
 * Wraps Open Trivia DB with the handling the old code was missing:
 *  - a persisted session token, so the API stops repeating questions you've already had;
 *  - spacing calls 5 s apart and retrying on the rate limit (code 5 / HTTP 429) instead of
 *    instantly dropping to the offline bank;
 *  - retrying "not enough questions" (code 1) with a smaller amount, then without the
 *    difficulty filter, instead of giving up;
 *  - resetting an exhausted token (code 4) and replacing an expired one (code 3).
 *
 * Returns an empty list when offline or when nothing could be fetched; callers fill from the
 * local bank.
 */
/** Where the OpenTDB session token is persisted (implemented by [QuizPrefs]; faked in tests). */
interface OpenTdbTokenStore {
    var openTdbToken: String?
}

class OpenTdbRemoteSource(
    private val prefs: OpenTdbTokenStore,
    private val service: OpenTdbService = OpenTdbApiClient.service,
    private val decode: (String) -> String = OpenTdbApiClient::unescapeHtmlString,
    private val minCallSpacingMs: Long = MIN_CALL_SPACING_MS
) {
    private val mutex = Mutex()
    private var lastApiCallAt = 0L

    private data class Request(val amount: Int, val categoryId: Int?, val difficulty: String?, val type: String?)

    /**
     * @param appTopic the app topic to fetch for, or null for any category.
     * @param difficulty "easy" | "medium" | "hard" | null for any.
     * @param type "multiple" | "boolean" | null for any.
     */
    suspend fun fetch(appTopic: String?, difficulty: String?, type: String?, amount: Int): List<Question> =
        mutex.withLock {
            if (amount <= 0) return@withLock emptyList()
            val categoryId = appTopic?.let { topic ->
                // A topic with no OpenTDB equivalent must not fall through to "any category".
                Categories.openTdbIdsFor(topic).randomOrNull() ?: return@withLock emptyList()
            }
            var request = Request(amount.coerceIn(1, MAX_AMOUNT), categoryId, difficulty, type)

            repeat(MAX_ATTEMPTS) {
                val token = ensureToken()
                val response = try {
                    spaceOutCalls()
                    service.getQuestions(request.amount, request.categoryId, request.difficulty, request.type, token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpException) {
                    if (e.code() == 429) null else return@withLock emptyList()
                } catch (e: IOException) {
                    Log.w(TAG, "OpenTDB unreachable: ${e.message}")
                    return@withLock emptyList() // offline: don't burn retries
                } catch (e: Exception) {
                    Log.e(TAG, "OpenTDB request failed", e)
                    return@withLock emptyList()
                }

                when (response?.responseCode ?: CODE_RATE_LIMIT) {
                    CODE_SUCCESS -> return@withLock response!!.results.mapNotNull { toQuestion(it, appTopic) }
                    CODE_NO_RESULTS -> request = relax(request) ?: return@withLock emptyList()
                    CODE_TOKEN_NOT_FOUND -> prefs.openTdbToken = null
                    CODE_TOKEN_EMPTY -> resetToken(token)
                    CODE_RATE_LIMIT -> Unit // spaceOutCalls() waits before the next attempt
                    else -> return@withLock emptyList()
                }
            }
            emptyList()
        }

    /** Ask for fewer questions first, then drop the difficulty filter. */
    private fun relax(request: Request): Request? = when {
        request.amount > MIN_RELAXED_AMOUNT -> request.copy(amount = (request.amount / 2).coerceAtLeast(MIN_RELAXED_AMOUNT))
        request.difficulty != null -> request.copy(difficulty = null)
        else -> null
    }

    private suspend fun spaceOutCalls() {
        val wait = lastApiCallAt + minCallSpacingMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastApiCallAt = System.currentTimeMillis()
    }

    private suspend fun ensureToken(): String? {
        prefs.openTdbToken?.let { return it }
        return try {
            service.requestToken().takeIf { it.responseCode == CODE_SUCCESS }?.token
                ?.also { prefs.openTdbToken = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Without a token the API still works, it just may repeat questions.
        }
    }

    private suspend fun resetToken(token: String?) {
        if (token == null) return
        try {
            val response = service.resetToken(token)
            if (response.responseCode != CODE_SUCCESS) prefs.openTdbToken = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.openTdbToken = null
        }
    }

    private fun toQuestion(item: OpenTdbResult, requestedTopic: String?): Question? {
        val text = decode(item.question)
        val correct = decode(item.correctAnswer)
        val options = if (item.type == "boolean") {
            listOf("True", "False")
        } else {
            (item.incorrectAnswers.map(decode) + correct).distinct()
        }
        val correctIndex = options.indexOfFirst { it.equals(correct, ignoreCase = true) }
        if (text.isBlank() || correctIndex < 0 || options.size < 2) return null // never mark a wrong answer as right

        return Question(
            text = text,
            optionsList = options,
            correctIndex = correctIndex,
            category = requestedTopic ?: Categories.appTopicForOpenTdbLabel(decode(item.category)),
            difficulty = item.difficulty.lowercase(Locale.ROOT),
            explanation = "The correct answer is: $correct."
        )
    }

    private companion object {
        const val TAG = "OpenTdbRemoteSource"
        const val MAX_AMOUNT = 50
        const val MIN_RELAXED_AMOUNT = 5
        const val MAX_ATTEMPTS = 4
        const val MIN_CALL_SPACING_MS = 5_100L

        const val CODE_SUCCESS = 0
        const val CODE_NO_RESULTS = 1
        const val CODE_TOKEN_NOT_FOUND = 3
        const val CODE_TOKEN_EMPTY = 4
        const val CODE_RATE_LIMIT = 5
    }
}
