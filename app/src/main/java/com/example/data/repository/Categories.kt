package com.example.data.repository

/**
 * Single source of truth for topic names and how they map onto Open Trivia DB categories.
 *
 * Previously several app topics (Food & Drink, Business, Law, ...) were silently mapped to
 * OpenTDB "General Knowledge" or "Science", so picking "Food & Drink" served unrelated questions
 * labelled with the wrong topic. Topics with no real OpenTDB equivalent now come only from the
 * bundled/cached question bank.
 */
object Categories {
    const val ALL_TOPICS = "All Topics"
    const val CAPITAL_CITIES = "Capital Cities Challenge"
    const val GEOGRAPHY = "Geography"
    const val GENERAL_KNOWLEDGE = "General Knowledge"

    /** App topic -> OpenTDB category ids it may draw from (one is picked at random per request). */
    private val openTdbIds: Map<String, List<Int>> = mapOf(
        "General Knowledge" to listOf(9),
        "Literature & Books" to listOf(10),
        // Film, Music, Television, Video Games, Cartoons (Musicals/Board Games are too small to filter well)
        "Entertainment" to listOf(11, 12, 14, 15, 32),
        "Science & Nature" to listOf(17),
        "Technology & Computing" to listOf(18, 30),
        "Mathematics & Logic" to listOf(19),
        "Religion & Mythology" to listOf(20),
        "Sports & Recreation" to listOf(21),
        "Geography" to listOf(22),
        "History" to listOf(23),
        "Politics & World Leaders" to listOf(24),
        "Art & Architecture" to listOf(25),
        "Pop Culture & Memes" to listOf(26, 29, 31),
        "Animals & Wildlife" to listOf(27)
    )

    fun openTdbIdsFor(appTopic: String): List<Int> = openTdbIds[appTopic].orEmpty()

    fun hasRemoteSource(appTopic: String): Boolean = openTdbIds.containsKey(appTopic)

    /** Maps an OpenTDB category label (e.g. "Entertainment: Video Games") back onto an app topic. */
    fun appTopicForOpenTdbLabel(label: String): String = when {
        label == "Entertainment: Books" -> "Literature & Books"
        label == "Entertainment: Comics" ||
            label == "Entertainment: Japanese Anime & Manga" ||
            label == "Celebrities" -> "Pop Culture & Memes"
        label.startsWith("Entertainment") -> "Entertainment"
        label == "Science: Computers" || label == "Science: Gadgets" -> "Technology & Computing"
        label == "Science: Mathematics" -> "Mathematics & Logic"
        label == "Science & Nature" -> "Science & Nature"
        label == "Mythology" -> "Religion & Mythology"
        label == "Sports" -> "Sports & Recreation"
        label == "Geography" -> "Geography"
        label == "History" -> "History"
        label == "Politics" -> "Politics & World Leaders"
        label == "Art" -> "Art & Architecture"
        label == "Animals" -> "Animals & Wildlife"
        else -> GENERAL_KNOWLEDGE // "General Knowledge", "Vehicles", anything new
    }
}
