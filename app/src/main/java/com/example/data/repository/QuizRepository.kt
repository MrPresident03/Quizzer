package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.data.dao.QuizDao
import com.example.data.model.Achievement
import com.example.data.model.Question
import com.example.data.model.QuizHistory
import com.example.data.model.UserStats
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.source
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

class QuizRepository(
    private val context: Context,
    private val quizDao: QuizDao,
    private val prefs: QuizPrefs = QuizPrefs(context),
    /** When given, multi-step writes run in one Room transaction (all-or-nothing). */
    private val database: RoomDatabase? = null
) {

    val userStats: Flow<UserStats?> = quizDao.getUserStatsFlow()
    val allHistory: Flow<List<QuizHistory>> = quizDao.getAllHistoryFlow()
    val allAchievements: Flow<List<Achievement>> = quizDao.getAllAchievementsFlow()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val initMutex = Mutex()
    @Volatile private var initialized = false

    private val mistakesLock = Any()
    private var mistakeBook: MistakeBook? = null
    private val _mistakeCount = MutableStateFlow(0)
    /** How many questions are waiting in "Review mistakes". */
    val mistakeCount: StateFlow<Int> = _mistakeCount.asStateFlow()

    private val _reminderShown = MutableStateFlow(computeReminderShown())
    /**
     * What the "Daily Reminder" switch should show: the player's choice AND the system-level
     * notification setting (which can be switched off outside the app).
     */
    val reminderShown: StateFlow<Boolean> = _reminderShown.asStateFlow()

    private fun computeReminderShown(): Boolean =
        prefs.dailyReminderEnabled && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Call whenever the app returns to the foreground: the system notification setting may have changed. */
    fun refreshReminderState() {
        _reminderShown.value = computeReminderShown()
    }

    /** Runs [block] atomically when a database was supplied (the app always does). */
    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        if (database != null) database.withTransaction { block() } else block()

    // --- SEEDING & INITIALIZATION ---

    /**
     * Safe to call repeatedly; quiz builders call it first so a quiz started on the very first launch
     * can't race the background seeding and come back empty.
     */
    suspend fun initializeAppIfNeeded() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        initMutex.withLock {
            if (initialized) return@withLock
            // The stats row comes first: seeding takes a while, and a theme or setting changed during
            // the first launch needs a row to land in (IGNORE, so it can never overwrite real progress).
            quizDao.insertUserStatsIfMissing(UserStats())
            syncBundledQuestions()
            // IGNORE (not REPLACE) so adding a new achievement in an update doesn't re-lock earned ones.
            quizDao.deleteAchievement("shop_spree") // the XP shop was removed
            quizDao.insertMissingAchievements(defaultAchievements())
            _mistakeCount.value = mistakes().size
            initialized = true
        }
    }

    /**
     * Makes the Room question table match assets/questions.json. Versioned, so a changed bank reaches
     * existing installs. It is an upsert keyed on the normalised question text, all in one
     * transaction: existing questions get the bank's latest wording / answers / explanation but keep
     * their id and answered / correct counters, new ones are inserted, and questions that are no
     * longer in the bank are removed (along with their entries in the mistakes list and seen log).
     * The old auto-generated placeholder questions are removed too.
     */
    private suspend fun syncBundledQuestions() {
        if (prefs.bundledSeedVersion >= BUNDLED_SEED_VERSION) return
        val bundled = loadBundledQuestions() ?: return // try again next launch
        if (applyBundledBank(bundled)) prefs.bundledSeedVersion = BUNDLED_SEED_VERSION
    }

    /** The upsert itself (see [syncBundledQuestions]). Returns false if it refused to touch the table. */
    internal suspend fun applyBundledBank(bundled: List<Question>): Boolean {
        if (bundled.isEmpty()) {
            // Never wipe the table because of an empty / unreadable bank.
            Log.e(TAG, "Bundled question bank has no playable questions; keeping the stored ones")
            return false
        }

        var removedLegacy = 0
        lateinit var plan: BankSyncPlan
        inTransaction {
            removedLegacy = quizDao.deleteLegacyGeneratedQuestions()
            plan = QuestionBankSync.plan(quizDao.getAllQuestions(), bundled)
            if (plan.update.isNotEmpty()) quizDao.updateQuestions(plan.update)
            if (plan.insert.isNotEmpty()) quizDao.insertQuestions(plan.insert)
            plan.deleteIds.chunked(DELETE_CHUNK).forEach { quizDao.deleteQuestionsByIds(it) }
        }

        if (plan.removedKeys.isNotEmpty()) {
            prefs.removeSeen(plan.removedKeys)
            updateMistakes { it.removeKeys(plan.removedKeys) }
        }
        if (plan.update.isNotEmpty()) updateMistakes { it.refreshFrom(plan.update) }
        _mistakeCount.value = mistakes().size
        Log.d(
            TAG,
            "Question bank synced: removed $removedLegacy placeholders, ${plan.deleteIds.size} retired, " +
                "updated ${plan.update.size}, added ${plan.insert.size}"
        )
        return true
    }

    /** Parses assets/questions.json (null if the file can't be read at all). Bad entries are skipped. */
    internal fun loadBundledQuestions(): List<Question>? = try {
        context.assets.open("questions.json").source().buffer().use { parseBundledQuestions(it) }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read bundled questions.json", e)
        null
    }

    /**
     * Streams the JSON array one entry at a time, so a single malformed entry (missing field, wrong
     * type, unplayable options) is logged and skipped instead of losing the whole bank. A file that
     * is not valid JSON at all still throws.
     */
    internal fun parseBundledQuestions(source: BufferedSource): List<Question> {
        val adapter = moshi.adapter(BundledQuestionJson::class.java)
        val reader = JsonReader.of(source)
        val result = ArrayList<Question>()
        var skipped = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val raw = reader.readJsonValue()
            val question = try {
                adapter.fromJsonValue(raw)?.let {
                    Question(
                        text = it.text.trim(),
                        optionsList = it.options.map(String::trim),
                        correctIndex = it.correctIndex,
                        category = it.category,
                        difficulty = it.difficulty.lowercase(Locale.ROOT),
                        explanation = it.explanation,
                        imageUrl = it.imageUrl
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable bundled entry: ${e.message}")
                null
            }
            if (question != null && QuestionPicker.isPlayable(question)) {
                result.add(question)
            } else {
                skipped++
                if (question != null) Log.w(TAG, "Skipping malformed bundled question: ${question.text}")
            }
        }
        reader.endArray()
        if (skipped > 0) Log.w(TAG, "Skipped $skipped bad entries in questions.json")
        return result
    }

    private fun defaultAchievements() = listOf(
        Achievement("first_quiz", "First Step", "Complete your first quiz session!", "ic_badge_1"),
        Achievement("first_perfect", "Perfect Blitz", "Get a perfect score in any practice or daily quiz!", "ic_badge_2"),
        Achievement("streak_3", "Spark of Fire", "Achieve a 3-day daily streak!", "ic_badge_3"),
        Achievement("streak_7", "Week on Fire", "Maintain a 7-day daily streak!", "ic_badge_4"),
        Achievement("questions_100", "Centurion", "Answer 100 questions correctly in total!", "ic_badge_5"),
        Achievement("science_master", "Newton's Apprentice", "Answer 50 science questions correctly!", "ic_badge_6"),
        Achievement("history_master", "Time Traveler", "Answer 50 history questions correctly!", "ic_badge_7"),
        Achievement("sports_master", "Olympian", "Answer 50 sports questions correctly!", "ic_badge_8"),
        Achievement("tech_master", "Technophile", "Answer 50 technology questions correctly!", "ic_badge_9"),
        Achievement("speed_demon", "Speed Demon", "Answer 10 questions in a row in under 10 seconds each!", "ic_badge_10"),
        Achievement("lifeline_lover", "Lifeline Lover", "Use 15 total lifelines during quizzes!", "ic_badge_11"),
        Achievement("flag_master", "Flag Expert", "Answer 50 flag questions correctly!", "ic_badge_12"),
        Achievement("hard_conqueror", "Hard Core", "Complete any quiz on Hard difficulty!", "ic_badge_13"),
        Achievement("endless_survivor", "Endless Survivor", "Score 30 points or more in Endless Practice Mode!", "ic_badge_14"),
        Achievement("rich_student", "XP Magnet", "Accumulate over 1,000 total XP points!", "ic_badge_15"),
        Achievement("mixed_master", "Jack of All Trades", "Complete a Mixed topics quiz of 30 questions!", "ic_badge_16"),
        Achievement("level_5s", "Level Up", "Reach Level 5 in progression!", "ic_badge_17"),
        Achievement("daily_champ", "Daily Champion", "Earn a perfect score in a Daily Challenge!", "ic_badge_18"),
        Achievement("half_hundred", "Half Centurion", "Complete 50 quizzes in total!", "ic_badge_19"),
        Achievement("grand_master", "Grand Master", "Reach Level 20 of progression!", "ic_badge_20")
    )

    // --- QUIZ BUILDING ---

    /**
     * Builds a practice quiz.
     *
     * Fully offline: questions come from the bundled bank in Room (only the selected topics, or a
     * random sample for all topics, are loaded), preferring never-seen and least-recently-seen
     * ones, balanced across the selected topics.
     *
     * @param excludeKeys question keys already used in this session (endless mode refills).
     */
    suspend fun getPracticeQuiz(
        categories: List<String>,
        difficulty: String, // "easy", "medium", "hard", "Mixed"
        count: Int,         // 10, 20, 30, or ENDLESS_COUNT
        format: String,     // "Multiple Choice", "True/False", "Mixed"
        excludeKeys: Set<String> = emptySet()
    ): List<Question> = withContext(Dispatchers.IO) {
        initializeAppIfNeeded()
        val random = Random.Default
        val qty = if (count == ENDLESS_COUNT) ENDLESS_BATCH_SIZE else count.coerceAtLeast(1)
        val diff = difficulty.lowercase(Locale.ROOT).takeIf { it in DIFFICULTIES }
        val apiType = when (format) {
            "Multiple Choice" -> "multiple"
            "True/False" -> "boolean"
            else -> null
        }
        val selected = categories.filter { it != Categories.ALL_TOPICS }.distinct()
        val wantsCapitals = Categories.CAPITAL_CITIES in selected
        val topics = selected - Categories.CAPITAL_CITIES
        val seen = prefs.seenSnapshot()
        val lastSeen: (String) -> Long? = { key -> seen[key] }

        // Flag and capital questions are multiple-choice only.
        val multipleChoiceAllowed = apiType != "boolean"
        var capitalCount = 0
        var flagCount = 0
        when {
            wantsCapitals && topics.isEmpty() -> capitalCount = qty
            wantsCapitals -> capitalCount = qty / (topics.size + 1)
        }
        if (multipleChoiceAllowed && Categories.GEOGRAPHY in topics) {
            val geographyShare = qty / (topics.size + if (wantsCapitals) 1 else 0)
            flagCount = geographyShare / 4
            capitalCount += geographyShare / 4
        } else if (multipleChoiceAllowed && selected.isEmpty()) {
            flagCount = qty / 10
            capitalCount = qty / 10
        }
        if (!multipleChoiceAllowed) capitalCount = 0 // capital questions have no True/False form
        val coreCount = qty - capitalCount - flagCount

        val core = buildCore(topics, diff, apiType, coreCount, lastSeen, excludeKeys, random)
        // Endless refill and the chosen topics have run dry: say so (empty) rather than padding the
        // run with flags or unrelated topics.
        if (excludeKeys.isNotEmpty() && coreCount > 0 && core.isEmpty()) return@withContext emptyList()
        val flags = if (flagCount > 0) {
            FlagQuestionsProvider.generateFlagQuiz("Mixed", difficulty, flagCount, lastSeen, random)
        } else emptyList()
        val capitals = if (capitalCount > 0) {
            CapitalCitiesProvider.generateCapitalQuiz(difficulty, capitalCount, lastSeen, random)
        } else emptyList()

        finalizeQuiz(core + flags + capitals, excludeKeys, random)
    }

    /**
     * Same 10 questions for every player on a given day (see [DailyChallenge]), built offline from
     * the bundled bank so results can be compared and shared.
     */
    suspend fun getDailyChallengeQuiz(date: LocalDate = LocalDate.now()): List<Question> = withContext(Dispatchers.IO) {
        initializeAppIfNeeded()
        val bundled = loadBundledQuestions().orEmpty() // on demand: no permanent in-memory copy
        if (bundled.size >= DailyChallenge.TRIVIA_PER_DAY) {
            return@withContext DailyChallenge.build(bundled, date)
        }
        // Bundled bank unreadable: fall back to a normal mixed quiz rather than nothing.
        val random = Random.Default
        val seen = prefs.seenSnapshot()
        val core = buildCore(emptyList(), null, null, DAILY_CORE_SIZE, { seen[it] }, emptySet(), random)
        finalizeQuiz(core, emptySet(), random)
    }

    fun todayDailyResult(today: LocalDate = LocalDate.now()): DailyResult? =
        DailyResult.parse(prefs.dailyResult)?.takeIf { it.date == today.toString() }

    /** The most recently saved Daily result, whatever day it was played (used for sharing). */
    fun lastDailyResult(): DailyResult? = DailyResult.parse(prefs.dailyResult)

    fun saveDailyResult(answers: List<Boolean>, seconds: Int, today: LocalDate = LocalDate.now()) {
        prefs.dailyResult = DailyResult(today.toString(), answers, seconds).serialize()
    }

    // --- MISTAKE REVIEW ---

    private fun mistakes(): MistakeBook = synchronized(mistakesLock) {
        mistakeBook ?: MistakeBook(parseMistakes(prefs.mistakesJson)).also { mistakeBook = it }
    }

    private fun parseMistakes(json: String?): List<MistakeEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { mistakesAdapter().fromJson(json) }.getOrNull().orEmpty()
    }

    private fun mistakesAdapter() =
        moshi.adapter<List<MistakeEntry>>(Types.newParameterizedType(List::class.java, MistakeEntry::class.java))

    private fun updateMistakes(change: (MistakeBook) -> Boolean) = synchronized(mistakesLock) {
        val book = mistakes()
        if (change(book)) {
            prefs.mistakesJson = mistakesAdapter().toJson(book.entries())
            _mistakeCount.value = book.size
        }
    }

    /** Up to [count] questions you previously got wrong, oldest misses first. */
    suspend fun getMistakeReviewQuiz(count: Int = REVIEW_SIZE): List<Question> = withContext(Dispatchers.IO) {
        initializeAppIfNeeded()
        val random = Random.Default
        mistakes().reviewSet(count).map { QuestionPicker.shuffleOptions(it, random) }
    }

    suspend fun getFlagQuiz(mode: String, difficulty: String, count: Int): List<Question> = withContext(Dispatchers.IO) {
        val seen = prefs.seenSnapshot()
        val random = Random.Default
        val flags = FlagQuestionsProvider.generateFlagQuiz(mode, difficulty, count, { seen[it] }, random)
        finalizeQuiz(flags, emptySet(), random)
    }

    suspend fun getCapitalQuiz(difficulty: String, count: Int): List<Question> = withContext(Dispatchers.IO) {
        val seen = prefs.seenSnapshot()
        val random = Random.Default
        val capitals = CapitalCitiesProvider.generateCapitalQuiz(difficulty, count, { seen[it] }, random)
        finalizeQuiz(capitals, emptySet(), random)
    }

    private suspend fun buildCore(
        topics: List<String>,
        difficulty: String?,
        apiType: String?,
        count: Int,
        lastSeen: (String) -> Long?,
        excludeKeys: Set<String>,
        random: Random
    ): List<Question> {
        if (count <= 0) return emptyList()
        val freshness: (Question) -> Long? = { lastSeen(QuestionPicker.keyOf(it)) }

        fun matchesFormat(q: Question) = when (apiType) {
            "boolean" -> QuestionPicker.isTrueFalse(q)
            "multiple" -> q.optionsList.size > 2
            else -> true
        }
        fun matches(q: Question, useDifficulty: Boolean) =
            (topics.isEmpty() || q.category in topics) &&
                (!useDifficulty || difficulty == null || q.difficulty.equals(difficulty, ignoreCase = true)) &&
                matchesFormat(q) &&
                QuestionPicker.keyOf(q) !in excludeKeys

        // 1. Load only what this quiz can use: the chosen topics, or a random sample for all topics.
        val sampleSize = maxOf(count * SAMPLE_FACTOR, MIN_SAMPLE)
        val local = (if (topics.isEmpty()) quizDao.getRandomQuestions(sampleSize) else quizDao.getQuestionsByCategories(topics))
            .filter(QuestionPicker::isPlayable)

        // 2. Freshest-first, balanced across the chosen topics.
        val pool = QuestionPicker.dedupe(local.filter { matches(it, true) })
        val buckets = if (topics.size <= 1) listOf(pool) else topics.map { topic -> pool.filter { it.category == topic } }
        var picked = QuestionPicker.balancedPick(
            buckets.map { QuestionPicker.orderByFreshness(it, freshness, random) },
            count,
            random
        )

        // 3. Too few for this difficulty? Top up from the same topics at any difficulty.
        if (picked.size < count && difficulty != null) {
            val have = picked.mapTo(HashSet()) { QuestionPicker.keyOf(it) }
            val extra = QuestionPicker.dedupe(local.filter { matches(it, false) && QuestionPicker.keyOf(it) !in have })
            picked = picked + QuestionPicker.pickFresh(extra, count - picked.size, freshness, random)
        }

        // 4. Still nothing at all (e.g. True/False on a topic with no T/F questions): use any topic.
        //    Only for a fresh quiz: an Endless refill (excludeKeys set) that finds nothing means the
        //    topic is used up, and the run ends instead of drifting into other topics.
        if (picked.isEmpty() && excludeKeys.isEmpty()) {
            val anyTopic = if (topics.isEmpty()) local else quizDao.getRandomQuestions(sampleSize).filter(QuestionPicker::isPlayable)
            val any = QuestionPicker.dedupe(anyTopic.filter { matchesFormat(it) && QuestionPicker.keyOf(it) !in excludeKeys })
            picked = QuestionPicker.pickFresh(any, count, freshness, random)
        }
        return picked
    }

    /** Dedupe, drop malformed rows, shuffle question order AND each question's answer order. */
    private fun finalizeQuiz(questions: List<Question>, excludeKeys: Set<String>, random: Random): List<Question> =
        QuestionPicker.dedupe(questions)
            .filter { QuestionPicker.isPlayable(it) && QuestionPicker.keyOf(it) !in excludeKeys }
            .shuffled(random)
            .map { QuestionPicker.shuffleOptions(it, random) }

    // --- PER-ANSWER TRACKING ---

    /**
     * Remembers the question as seen (so it's deprioritised next time), updates its counters, and
     * adds it to / clears it from the mistakes list. Skips pass [countAsMistake] = false.
     */
    suspend fun recordAnswer(question: Question, isCorrect: Boolean, countAsMistake: Boolean = true) = withContext(Dispatchers.IO) {
        prefs.markSeen(listOf(QuestionPicker.keyOf(question)))
        quizDao.incrementAnswerStats(question.text, if (isCorrect) 1 else 0)
        when {
            isCorrect -> updateMistakes { it.recordRight(question) }
            countAsMistake -> updateMistakes { it.recordWrong(question, System.currentTimeMillis()); true }
        }
    }

    /** Per-topic answered/correct totals, most-played first. */
    fun topicStats(): List<TopicStat> = prefs.answeredTopics()
        .map { topic ->
            val answered = prefs.categoryAnswered(topic)
            TopicStat(topic, answered, prefs.categoryCorrect(topic).coerceAtMost(answered))
        }
        .filter { it.answered > 0 }
        .sortedByDescending { it.answered }

    var isDailyReminderEnabled: Boolean
        get() = prefs.dailyReminderEnabled
        set(value) {
            prefs.dailyReminderEnabled = value
            refreshReminderState()
        }

    fun isDailyPlayed(today: LocalDate = LocalDate.now()): Boolean = prefs.dailyPlayedDate == today.toString()

    fun markDailyPlayed(today: LocalDate = LocalDate.now()) {
        prefs.dailyPlayedDate = today.toString()
    }

    // --- PROGRESSION & GAMIFICATION STATS UPDATE ---

    /**
     * Saves the session, updates stats/streak/level and unlocks achievements. Returns XP earned.
     *
     * Everything is written in one transaction and runs [NonCancellable], so leaving the screen or
     * starting a new quiz while this is running can't leave a half-saved session behind. Stats are
     * changed with partial UPDATEs, so a settings or theme change made at the same moment survives.
     */
    suspend fun recordQuizSession(summary: SessionSummary): Int = withContext(Dispatchers.IO + NonCancellable) {
        if (summary.totalCount == 0) return@withContext 0
        val today = LocalDate.now()
        val xpEarned = Scoring.sessionXp(summary.questions, summary.answers, summary.type)
        val counts = summary.countsTowardProgress

        inTransaction {
            quizDao.insertUserStatsIfMissing(UserStats())
            quizDao.insertHistory(
                QuizHistory(
                    categoryName = summary.categoryLabel,
                    score = summary.correctCount,
                    totalQuestions = summary.totalCount,
                    difficulty = summary.selectedDifficulty,
                    timeTakenSeconds = summary.durationSeconds,
                    xpEarned = xpEarned,
                    quizType = summary.type.label
                )
            )

            val stats = quizDao.getUserStats() ?: UserStats()
            val lifetimeXp = quizDao.getLifetimeXp()
            val newLevel = Scoring.levelForLifetimeXp(lifetimeXp)
            quizDao.addSessionTotals(summary.totalCount, summary.correctCount, xpEarned, newLevel)

            var streakNow = stats.currentStreak
            if (counts) {
                val streak = Scoring.nextStreak(
                    currentStreak = stats.currentStreak,
                    lastPlayed = Scoring.parseDate(stats.lastQuizDateStr),
                    today = today,
                    freezesOwned = stats.hasStreakFreeze
                )
                // Every 7 days in a row earns a streak freeze (up to MAX_STREAK_FREEZES), replacing the old shop item.
                val earnedFreeze = streak.streak > stats.currentStreak && streak.streak % FREEZE_EVERY_DAYS == 0
                val freezes = (stats.hasStreakFreeze - streak.freezesUsed + if (earnedFreeze) 1 else 0)
                    .coerceIn(0, MAX_STREAK_FREEZES)
                quizDao.setStreak(streak.streak, freezes, today.toString())
                streakNow = streak.streak
            }
            val updatedStats = quizDao.getUserStats() ?: stats

            // Per-topic counters (topic mastery panel + category achievements).
            val correctByCategory = summary.questions
                .filterIndexed { index, _ -> summary.answers.getOrNull(index) == true }
                .groupingBy { statsTopicFor(it.category) }
                .eachCount()
            val answeredByCategory = summary.questions
                .take(summary.answers.size)
                .groupingBy { statsTopicFor(it.category) }
                .eachCount()
            prefs.addCategoryCorrect(correctByCategory)
            prefs.addCategoryAnswered(answeredByCategory)
            prefs.addLifelinesUsed(summary.lifelinesUsed)
            // Filed under the day the Daily was started, even if it finished after midnight.
            if (summary.type == SessionType.DAILY) markDailyPlayed(summary.dailyDate ?: today)

            val totalQuizzes = quizDao.getCountedHistoryCount(Scoring.MIN_COUNTED_ANSWERS)
            val unlocks = buildList {
                if (counts) add("first_quiz")
                if (summary.isPerfect) add("first_perfect")
                if (counts && streakNow >= 3) add("streak_3")
                if (counts && streakNow >= 7) add("streak_7")
                if (updatedStats.correctAnswersCount >= 100) add("questions_100")
                if (prefs.categoryCorrect("Science & Nature") >= 50) add("science_master")
                if (prefs.categoryCorrect("History") >= 50) add("history_master")
                if (prefs.categoryCorrect("Sports & Recreation") >= 50) add("sports_master")
                if (prefs.categoryCorrect("Technology & Computing") >= 50) add("tech_master")
                if (prefs.categoryCorrect("Flags") >= 50) add("flag_master")
                if (summary.bestSpeedStreak >= 10) add("speed_demon")
                if (prefs.lifelinesUsedTotal >= 15) add("lifeline_lover")
                if (counts && summary.selectedDifficulty.equals("hard", ignoreCase = true)) add("hard_conqueror")
                if (summary.type == SessionType.ENDLESS && summary.correctCount >= 30) add("endless_survivor")
                if (lifetimeXp >= 1000) add("rich_student")
                if (counts && summary.isMixedTopics && summary.totalCount >= 30) add("mixed_master")
                if (updatedStats.currentLevel >= 5) add("level_5s")
                if (summary.type == SessionType.DAILY && summary.isPerfect) add("daily_champ")
                if (totalQuizzes >= 50) add("half_hundred")
                if (updatedStats.currentLevel >= 20) add("grand_master")
            }
            unlocks.forEach { unlockAchievement(it) }
        }
        xpEarned
    }

    private suspend fun unlockAchievement(id: String) {
        if (quizDao.unlockAchievement(id, System.currentTimeMillis()) > 0) {
            Log.d(TAG, "Achievement UNLOCKED: $id")
        }
    }

    // --- THEMES & QUICK PLAY ---

    suspend fun selectTheme(themeId: String) = withContext(Dispatchers.IO) {
        quizDao.insertUserStatsIfMissing(UserStats())
        quizDao.setActiveTheme(themeId)
    }

    fun lastPracticeSettings(): PracticeSettings = PracticeSettings.parse(prefs.lastPractice) ?: PracticeSettings()

    fun saveLastPracticeSettings(settings: PracticeSettings) {
        prefs.lastPractice = settings.serialize()
    }

    // --- SETTINGS ---

    /** Writes only the three toggles, leaving the profile and everything else untouched. */
    suspend fun updateToggles(sound: Boolean, haptic: Boolean, textLarge: Boolean) = withContext(Dispatchers.IO) {
        quizDao.insertUserStatsIfMissing(UserStats())
        quizDao.setToggles(sound, haptic, if (textLarge) "large" else "normal")
    }

    /** Writes only the name and avatar; a blank name keeps the current one. */
    suspend fun updateProfile(displayName: String, avatar: String) = withContext(Dispatchers.IO) {
        quizDao.insertUserStatsIfMissing(UserStats())
        val name = displayName.trim().takeIf { it.isNotBlank() }?.take(MAX_NAME_LENGTH)
        if (name != null) quizDao.setProfile(name, avatar) else quizDao.setAvatar(avatar)
    }

    suspend fun updateConfig(sound: Boolean, haptic: Boolean, textLarge: Boolean, displayName: String, avatar: String) {
        inTransaction {
            updateToggles(sound, haptic, textLarge)
            updateProfile(displayName, avatar)
        }
    }

    suspend fun resetAllProgress() = withContext(Dispatchers.IO) {
        inTransaction {
            quizDao.clearHistory()
            quizDao.resetAchievements()
            quizDao.insertOrUpdateUserStats(UserStats())
        }
        prefs.resetProgress()
        synchronized(mistakesLock) { mistakeBook = MistakeBook() }
        _mistakeCount.value = 0
    }

    /** "Recycle seen questions": forget what's been shown so older questions can come back. */
    suspend fun clearQuestionHistory() = withContext(Dispatchers.IO) {
        quizDao.resetAnswerStats()
        prefs.clearSeen()
    }

    companion object {
        private const val TAG = "QuizRepository"

        /** Bump when assets/questions.json changes so existing installs pick up the new questions. */
        const val BUNDLED_SEED_VERSION = 5

        const val ENDLESS_COUNT = 999
        const val ENDLESS_BATCH_SIZE = 20
        private const val DAILY_CORE_SIZE = 6
        const val REVIEW_SIZE = 10
        private const val FREEZE_EVERY_DAYS = 7
        private const val MAX_STREAK_FREEZES = 2
        private const val MAX_NAME_LENGTH = 24
        private const val DELETE_CHUNK = 500 // Android SQLite: at most 999 bound variables per statement
        private const val SAMPLE_FACTOR = 40
        private const val MIN_SAMPLE = 800
        private val DIFFICULTIES = setOf("easy", "medium", "hard")
    }
}

/** Shape of each entry in assets/questions.json. */
@JsonClass(generateAdapter = true)
internal data class BundledQuestionJson(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: String,
    val difficulty: String,
    val explanation: String,
    val imageUrl: String? = null
)
