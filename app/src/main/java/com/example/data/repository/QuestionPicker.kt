package com.example.data.repository

import com.example.data.model.Question
import kotlin.random.Random

/**
 * Pure selection helpers (no Android dependencies) so the "variety" logic can be unit tested.
 *
 * The core idea: every question gets a stable key derived from its text. The app remembers when
 * each key was last shown ([SeenLog]). When building a quiz we always prefer questions that have
 * never been shown, then the ones shown longest ago — and we shuffle within those groups so two
 * quizzes with the same settings don't come out identical.
 */
object QuestionPicker {

    /** Lower-cases and collapses whitespace so cosmetic differences don't create "new" questions. */
    fun normalizedText(text: String): String =
        text.trim().lowercase().replace(WHITESPACE, " ")

    /** Stable 64-bit FNV-1a hash of the normalized text, as hex. Compact enough to store hundreds of. */
    fun keyOf(text: String): String {
        var hash = FNV_OFFSET
        for (ch in normalizedText(text)) {
            hash = hash xor ch.code.toLong()
            hash *= FNV_PRIME
        }
        return java.lang.Long.toHexString(hash)
    }

    fun keyOf(question: Question): String = keyOf(question.text)

    /**
     * Returns up to [count] items, never-seen first (in random order), then previously seen items
     * oldest-first. [lastSeen] returns the epoch millis an item was last shown, or null if never.
     */
    fun <T> pickFresh(
        candidates: List<T>,
        count: Int,
        lastSeen: (T) -> Long?,
        random: Random = Random.Default
    ): List<T> = orderByFreshness(candidates, lastSeen, random).take(count.coerceAtLeast(0))

    /** Same ordering as [pickFresh] but returns everything, for callers that need to keep leftovers. */
    fun <T> orderByFreshness(
        candidates: List<T>,
        lastSeen: (T) -> Long?,
        random: Random = Random.Default
    ): List<T> {
        val shuffled = candidates.shuffled(random)
        val unseen = shuffled.filter { lastSeen(it) == null }
        // sortedBy is stable, so equal timestamps keep their shuffled order.
        val seen = shuffled.filter { lastSeen(it) != null }.sortedBy { lastSeen(it) }
        return unseen + seen
    }

    /**
     * Takes items round-robin from each bucket (bucket order re-shuffled every round) so a quiz
     * built from several topics is genuinely mixed instead of being dominated by one topic.
     * Each bucket should already be in preference order (e.g. from [orderByFreshness]).
     */
    fun <T> balancedPick(buckets: List<List<T>>, count: Int, random: Random = Random.Default): List<T> {
        if (count <= 0) return emptyList()
        val iterators = buckets.filter { it.isNotEmpty() }.map { it.iterator() }
        val result = ArrayList<T>(count)
        while (result.size < count) {
            val live = iterators.filter { it.hasNext() }
            if (live.isEmpty()) break
            for (iterator in live.shuffled(random)) {
                if (result.size >= count) break
                result.add(iterator.next())
            }
        }
        return result
    }

    /** Removes questions with the same normalized text, keeping the first occurrence. */
    fun dedupe(questions: List<Question>): List<Question> = questions.distinctBy { keyOf(it) }

    fun isTrueFalse(question: Question): Boolean =
        question.optionsList.size == 2 &&
            question.optionsList.map { it.trim().lowercase() }.toSet() == setOf("true", "false")

    /**
     * Shuffles the answer options and remaps [Question.correctIndex], so the right answer isn't
     * always in the same slot. True/False questions are normalised to "True, False" order instead.
     */
    fun shuffleOptions(question: Question, random: Random = Random.Default): Question {
        val options = question.optionsList
        if (options.size < 2 || question.correctIndex !in options.indices) return question

        if (isTrueFalse(question)) {
            val answerIsTrue = options[question.correctIndex].trim().equals("true", ignoreCase = true)
            return question.copy(optionsList = listOf("True", "False"), correctIndex = if (answerIsTrue) 0 else 1)
        }

        val order = options.indices.shuffled(random)
        return question.copy(
            optionsList = order.map { options[it] },
            correctIndex = order.indexOf(question.correctIndex)
        )
    }

    /** Basic sanity check so one malformed row can't crash or soft-lock a quiz. */
    fun isPlayable(question: Question): Boolean =
        question.text.isNotBlank() &&
            question.optionsList.size >= 2 &&
            question.correctIndex in question.optionsList.indices &&
            question.optionsList.none { it.isBlank() }

    private val WHITESPACE = Regex("\\s+")
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L
}

/**
 * In-memory record of when each question key was last shown, capped to [capacity] entries
 * (oldest dropped first).
 *
 * The cap is above the size of the bundled question bank (about 14,500 questions), so a long-time
 * player's whole history is kept. To keep that affordable in SharedPreferences, question keys (64-bit
 * hex hashes) are stored as base-36 numbers with each timestamp as a delta from the previous entry
 * (about 18 bytes per question instead of 30). Logs with other kinds of keys use the older, larger
 * "millis<TAB>key" per line format, which [parse] still reads.
 */
class SeenLog(private val capacity: Int = DEFAULT_CAPACITY) {

    // Iteration order = least recently marked first, because mark() re-inserts the key.
    private val entries = LinkedHashMap<String, Long>()

    val size: Int get() = entries.size

    fun lastSeen(key: String): Long? = entries[key]

    fun mark(key: String, at: Long) {
        entries.remove(key)
        entries[key] = at
        while (entries.size > capacity) {
            entries.remove(entries.keys.first())
        }
    }

    fun remove(key: String) {
        entries.remove(key)
    }

    fun clear() = entries.clear()

    fun snapshot(): Map<String, Long> = HashMap(entries)

    fun serialize(): String {
        if (entries.isEmpty() || !entries.keys.all { HEX_KEY.matches(it) }) return serializeLegacy()
        val out = StringBuilder(entries.size * 18 + 3).append(COMPACT_HEADER)
        var previous = 0L
        for ((key, at) in entries) {
            out.append(java.lang.Long.toUnsignedString(java.lang.Long.parseUnsignedLong(key, 16), 36))
                .append(':')
                .append(java.lang.Long.toString(at - previous, 36))
                .append(',')
            previous = at
        }
        return out.toString()
    }

    private fun serializeLegacy(): String = entries.entries.joinToString("\n") { "${it.value}\t${it.key}" }

    companion object {
        const val DEFAULT_CAPACITY = 30_000

        private const val COMPACT_HEADER = "v2\n"
        // What QuestionPicker.keyOf produces: unsigned hex without leading zeros (so it round-trips exactly).
        private val HEX_KEY = Regex("^(0|[1-9a-f][0-9a-f]{0,15})$")

        fun parse(serialized: String?, capacity: Int = DEFAULT_CAPACITY): SeenLog {
            val log = SeenLog(capacity)
            if (serialized == null) return log
            if (serialized.startsWith(COMPACT_HEADER)) {
                var at = 0L
                for (token in serialized.substring(COMPACT_HEADER.length).split(',')) {
                    val colon = token.indexOf(':')
                    if (colon <= 0) continue
                    try {
                        val key = java.lang.Long.toHexString(java.lang.Long.parseUnsignedLong(token.substring(0, colon), 36))
                        at += java.lang.Long.parseLong(token.substring(colon + 1), 36)
                        log.mark(key, at)
                    } catch (e: NumberFormatException) {
                        // Skip a damaged token rather than losing the whole log.
                    }
                }
                return log
            }
            serialized.lineSequence().forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val at = line.substring(0, tab).toLongOrNull()
                    val key = line.substring(tab + 1)
                    if (at != null && key.isNotEmpty()) log.mark(key, at)
                }
            }
            return log
        }
    }
}
