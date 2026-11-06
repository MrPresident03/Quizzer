package com.example.data.repository

import com.example.data.model.Question

/** Short messages shown when a quiz isn't exactly what the player asked for. */
object QuizNotices {

    /** "Fashion & Design", "your selected topics" or "all topics". */
    fun topicLabel(categories: List<String>): String {
        val selected = categories.filter { it != Categories.ALL_TOPICS }.distinct()
        return when {
            selected.isEmpty() -> "all topics"
            selected.size == 1 -> selected.first()
            else -> "your selected topics"
        }
    }

    private fun formatWord(format: String): String = when (format) {
        "True/False" -> "True/False "
        "Multiple Choice" -> "multiple-choice "
        else -> ""
    }

    /**
     * A notice for a practice quiz that came out different from the request, or null if it is fine:
     *  - nothing matched the chosen topics, so mixed topics are played instead;
     *  - fewer questions exist than requested ("Only 6 True/False questions in Fashion & Design - playing 6").
     * Endless runs have no fixed length, so they never get a notice.
     */
    fun forPractice(requested: Int, questions: List<Question>, categories: List<String>, format: String): String? {
        if (requested == QuizRepository.ENDLESS_COUNT || questions.isEmpty()) return null
        val chosen = categories.filter { it != Categories.ALL_TOPICS }.distinct()
        val label = topicLabel(categories)
        val kind = formatWord(format)
        // Geography is topped up with generated flag / capital questions, so it can't be judged by category.
        if (chosen.isNotEmpty() && Categories.GEOGRAPHY !in chosen && questions.none { it.category in chosen }) {
            return "No ${kind}questions in $label – playing mixed topics instead"
        }
        val got = questions.size
        if (got < requested) {
            return "Only $got ${kind}question${if (got == 1) "" else "s"} in $label – playing $got"
        }
        return null
    }

    /** Shown on the results of an Endless run that ended because nothing was left to ask. */
    fun endlessExhausted(categories: List<String>): String {
        val chosen = categories.filter { it != Categories.ALL_TOPICS }.distinct()
        val where = when {
            chosen.isEmpty() -> "the question bank"
            chosen.size == 1 -> chosen.first()
            else -> "your selected topics"
        }
        return "You've answered every question in $where!"
    }
}
