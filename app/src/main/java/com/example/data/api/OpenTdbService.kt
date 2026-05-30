package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.http.GET
import retrofit2.http.Query

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
    @Json(name = "results") val results: List<OpenTdbResult>
)

interface OpenTdbService {
    @GET("api.php")
    suspend fun getQuestions(
        @Query("amount") amount: Int,
        @Query("category") category: Int?,
        @Query("difficulty") difficulty: String?,
        @Query("type") type: String?
    ): OpenTdbResponse
}

object OpenTdbApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://opentdb.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: OpenTdbService = retrofit.create(OpenTdbService::class.java)

    /**
     * Decodes HTML decimal/hex entity sequences (like &quot;, &#039;, &amp;, etc.) into plain text.
     */
    fun unescapeHtmlString(text: String): String {
        return try {
            // Use Android's standard HTML decoder
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Throwable) {
            // Defensive local replacement if android.text.Html is unavailable (like in pure unit tests)
            text.replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&deg;", "°")
                .replace("&eacute;", "é")
                .replace("&oacute;", "ó")
                .replace("&iacute;", "í")
                .replace("&aacute;", "á")
                .replace("&uacute;", "ú")
                .replace("&ntilde;", "ñ")
        }
    }
}
