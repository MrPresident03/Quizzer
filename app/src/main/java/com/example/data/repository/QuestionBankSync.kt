package com.example.data.repository

import com.example.data.model.Question

/** What it takes to make the stored question table match the bundled bank. */
internal data class BankSyncPlan(
    val insert: List<Question>,
    /** Bundled versions of stored questions that changed; already carrying the stored id and counters. */
    val update: List<Question>,
    val deleteIds: List<Int>,
    /** [QuestionPicker.keyOf] keys of questions that are going away (for the seen log and mistakes list). */
    val removedKeys: Set<String>
)

/** Pure planning step of the bundled-bank upsert, kept free of Room so it is easy to test. */
internal object QuestionBankSync {

    /**
     * Questions are matched by [QuestionPicker.normalizedText]. A stored question that is also in the
     * bank takes the bank's wording, options, answer, category, difficulty, explanation and image, but
     * keeps its id and answered / correct counters. Stored questions missing from the bank are deleted;
     * bank questions that aren't stored yet are inserted. Duplicate rows for one question collapse to
     * the one with the most answers.
     */
    fun plan(existing: List<Question>, bundled: List<Question>): BankSyncPlan {
        val bundledByKey = LinkedHashMap<String, Question>()
        for (question in bundled) bundledByKey.putIfAbsent(QuestionPicker.normalizedText(question.text), question)

        val keptByKey = HashMap<String, Question>()
        val deleteIds = ArrayList<Int>()
        val removedKeys = HashSet<String>()
        for (row in existing) {
            val key = QuestionPicker.normalizedText(row.text)
            if (key !in bundledByKey) {
                deleteIds.add(row.id)
                removedKeys.add(QuestionPicker.keyOf(row.text))
                continue
            }
            val previous = keptByKey[key]
            if (previous == null) {
                keptByKey[key] = row
            } else if (row.answeredCount > previous.answeredCount) {
                keptByKey[key] = row
                deleteIds.add(previous.id)
            } else {
                deleteIds.add(row.id)
            }
        }

        val insert = ArrayList<Question>()
        val update = ArrayList<Question>()
        for ((key, question) in bundledByKey) {
            val stored = keptByKey[key]
            if (stored == null) {
                insert.add(question)
            } else {
                val merged = question.copy(
                    id = stored.id,
                    answeredCount = stored.answeredCount,
                    correctCount = stored.correctCount
                )
                if (merged != stored) update.add(merged)
            }
        }
        return BankSyncPlan(insert, update, deleteIds, removedKeys)
    }
}
