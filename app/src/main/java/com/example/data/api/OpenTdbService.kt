package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenTdbResult(
    @Json(name = "category") val category: String,
    @Json(name = "type") val type: String,
    @Json(name = "difficulty") val difficulty: String,
    @Json(name = "question") val question: String,
    @Json(name = "correct_answer") val correctAnswer: String,
    @Json(name = "incorrect_answers") val incorrectAnswers: List<String>
)

@JsonClass(generateAdapter = true)
data class OpenTdbResponse(
    @Json(name = "response_code") val responseCode: Int,
    // Error responses (codes 1-5) can omit results, so this must not be a required field.
    @Json(name = "results") val results: List<OpenTdbResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenTdbTokenResponse(
    @Json(name = "response_code") val responseCode: Int,
    @Json(name = "token") val token: String? = null
)

/**
 * Open Trivia DB response codes:
 * 0 success, 1 not enough questions for the query, 2 invalid parameter,
 * 3 token not found, 4 token has returned every question for the query, 5 rate limited (1 call / 5 s per IP).
 */
interface OpenTdbService {
    @GET("api.php")
    suspend fun getQuestions(
        @Query("amount") amount: Int,
        @Query("category") category: Int?,
        @Query("difficulty") difficulty: String?,
        @Query("type") type: String?,
        // A session token makes OpenTDB never return the same question twice until it's reset.
        @Query("token") token: String?
    ): OpenTdbResponse

    @GET("api_token.php?command=request")
    suspend fun requestToken(): OpenTdbTokenResponse

    @GET("api_token.php?command=reset")
    suspend fun resetToken(@Query("token") token: String): OpenTdbTokenResponse
}

object OpenTdbApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://opentdb.com/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: OpenTdbService = retrofit.create(OpenTdbService::class.java)

    /**
     * Decodes HTML decimal/hex entity sequences (like &quot;, &#039;, &amp;, etc.) into plain text.
     */
    fun unescapeHtmlString(text: String): String {
        return try {
            // Use Android's standard HTML decoder
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Throwable) {
            // Defensive local replacement if android.text.Html is unavailable (like in pure unit tests)
            text.replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&deg;", "°")
                .replace("&eacute;", "é")
                .replace("&oacute;", "ó")
                .replace("&iacute;", "í")
                .replace("&aacute;", "á")
                .replace("&uacute;", "ú")
                .replace("&ntilde;", "ñ")
                .replace("&amp;", "&") // last, so "&amp;quot;" doesn't get double-decoded
        }
    }
}
