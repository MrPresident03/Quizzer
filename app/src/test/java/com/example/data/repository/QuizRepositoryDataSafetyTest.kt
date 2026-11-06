package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.QuizDao
import com.example.data.db.AppDatabase
import com.example.data.model.Question
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bank upserts, atomic saves, per-entry parsing and the other data-safety rules, on in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuizRepositoryDataSafetyTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: QuizDao
    private lateinit var prefs: QuizPrefs
    private lateinit var repo: QuizRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.quizDao()
        prefs = QuizPrefs(context)
        prefs.bundledSeedVersion = QuizRepository.BUNDLED_SEED_VERSION // tests supply their own bank
        repo = QuizRepository(context, dao, prefs, db)
    }

    @After
    fun tearDown() = db.close()

    private fun q(
        text: String,
        category: String = "Topic",
        options: List<String> = listOf("a", "b", "c", "d"),
        correct: Int = 0,
        explanation: String = "because",
        difficulty: String = "easy"
    ) = Question(
        text = text, optionsList = options, correctIndex = correct, category = category,
        difficulty = difficulty, explanation = explanation
    )

    private fun tf(text: String, category: String = "Topic") =
        q(text, category, options = listOf("True", "False"), correct = 0)

    private suspend fun stored(text: String): Question? =
        dao.getAllQuestions().firstOrNull { QuestionPicker.normalizedText(it.text) == QuestionPicker.normalizedText(text) }

    // --- A3: bank updates reach existing installs ---

    @Test
    fun bankUpsertKeepsCountsAppliesChangesRemovesAndAdds() = runBlocking {
        val set1 = listOf(q("Question one?"), q("Question two?"), q("Question three?"))
        assertTrue(repo.applyBundledBank(set1))
        dao.incrementAnswerStats("Question one?", 1)
        dao.incrementAnswerStats("Question one?", 0)
        dao.incrementAnswerStats("Question three?", 1)
        val oneBefore = stored("Question one?")!!
        assertEquals(2, oneBefore.answeredCount)
        assertEquals(1, oneBefore.correctCount)

        // Question two is about to be removed: it sits in the mistakes list and the seen log.
        repo.recordAnswer(set1[1], isCorrect = false)
        assertEquals(1, repo.mistakeCount.value)
        assertNotNull(prefs.seenSnapshot()[QuestionPicker.keyOf("Question two?")])

        // Set 2: one answer / wording changed, one question removed, one added.
        val set2 = listOf(
            q("Question one?", options = listOf("w", "x", "y", "z"), correct = 3, explanation = "fixed", category = "Moved", difficulty = "hard"),
            q("Question three?"),
            q("Question four?")
        )
        assertTrue(repo.applyBundledBank(set2))

        val all = dao.getAllQuestions()
        assertEquals(3, all.size)
        val one = stored("Question one?")!!
        assertEquals(oneBefore.id, one.id)
        assertEquals(2, one.answeredCount)
        assertEquals(1, one.correctCount)
        assertEquals(listOf("w", "x", "y", "z"), one.optionsList)
        assertEquals(3, one.correctIndex)
        assertEquals("fixed", one.explanation)
        assertEquals("Moved", one.category)
        assertEquals("hard", one.difficulty)
        assertEquals(1, stored("Question three?")!!.answeredCount) // untouched question keeps its counts
        assertNull(stored("Question two?"))
        assertNotNull(stored("Question four?"))
        assertEquals(0, stored("Question four?")!!.answeredCount)

        // Dead keys are pruned from the mistakes list and the seen log.
        assertEquals(0, repo.mistakeCount.value)
        assertNull(prefs.seenSnapshot()[QuestionPicker.keyOf("Question two?")])
    }

    @Test
    fun bankUpsertRefreshesStaleMistakeCopiesAndIgnoresCosmeticTextChanges() = runBlocking {
        val original = q("Capital of X?", options = listOf("A", "B", "C"), correct = 0)
        repo.applyBundledBank(listOf(original))
        repo.recordAnswer(original, isCorrect = false)

        repo.applyBundledBank(listOf(q("  capital  of X?", options = listOf("A", "B", "C"), correct = 2)))

        assertEquals(1, dao.getAllQuestions().size) // same normalised text: updated, not duplicated
        val review = repo.getMistakeReviewQuiz()
        assertEquals(1, review.size)
        assertEquals("C", review[0].optionsList[review[0].correctIndex]) // reviewed against the corrected answer
    }

    @Test
    fun bankUpsertNeverWipesTheTableForAnEmptyBank() = runBlocking {
        repo.applyBundledBank(listOf(q("Keep me?")))
        assertFalse(repo.applyBundledBank(emptyList()))
        assertEquals(1, dao.getAllQuestions().size)
    }

    @Test
    fun bankUpsertHandlesMoreRowsThanSqliteVariableLimit() = runBlocking {
        repo.applyBundledBank((0 until 1_500).map { q("Old question $it?") })
        repo.applyBundledBank(listOf(q("Only survivor?")))
        assertEquals(listOf("Only survivor?"), dao.getAllQuestions().map { it.text })
    }

    // --- A4: atomic, cancellation-safe, non-clobbering saves ---

    private fun summary(answers: List<Boolean> = List(5) { true }, type: SessionType = SessionType.PRACTICE) = SessionSummary(
        type = type,
        questions = answers.indices.map { q("Played $it?") },
        answers = answers,
        selectedDifficulty = "Mixed",
        categoryLabel = "Topic",
        isMixedTopics = false,
        durationSeconds = 20,
        bestSpeedStreak = 0,
        lifelinesUsed = 0
    )

    @Test
    fun sessionSaveFinishesEvenIfTheCallerIsCancelled() = runBlocking {
        repo.initializeAppIfNeeded()
        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { repo.recordQuizSession(summary()) }
        job.cancel() // e.g. a new quiz starting while the save is running
        job.join()
        assertEquals(1, dao.getHistoryCount())
        val stats = dao.getUserStats()!!
        assertEquals(5, stats.totalQuestionsAnswered)
        assertEquals(1, stats.currentStreak)
        assertTrue(dao.getAllAchievements().first { it.id == "first_quiz" }.isUnlocked)
    }

    @Test
    fun themeChosenBeforeFirstLaunchSetupFinishesIsKept() = runBlocking {
        repo.selectTheme("forest_green") // no stats row exists yet
        repo.initializeAppIfNeeded()
        assertEquals("forest_green", dao.getUserStats()!!.activeThemeId)
    }

    @Test
    fun settingsThemeAndSessionWritesDoNotClobberEachOther() = runBlocking {
        repo.initializeAppIfNeeded()
        repo.selectTheme("sunset_orange")
        repo.updateToggles(sound = false, haptic = false, textLarge = true)
        repo.updateProfile("  Alex  ", "avatar_3")
        repo.recordQuizSession(summary())
        repo.updateProfile("   ", "avatar_4") // blank name keeps the current one

        val stats = dao.getUserStats()!!
        assertEquals("sunset_orange", stats.activeThemeId)
        assertFalse(stats.isSoundEffectsEnabled)
        assertFalse(stats.isHapticFeedbackEnabled)
        assertEquals("large", stats.fontSizePreference)
        assertEquals("Alex", stats.displayName)
        assertEquals("avatar_4", stats.avatarId)
        assertEquals(5, stats.totalQuestionsAnswered)
        assertEquals(1, stats.currentStreak)
    }

    // --- A5 / A7: Endless runs dry, capitals respect True/False ---

    @Test
    fun endlessRefillReturnsNothingOnceTheTopicIsExhausted() = runBlocking {
        val solo = (0 until 8).map { q("Solo question $it?", category = "Solo") }
        val others = (0 until 40).map { q("Other question $it?", category = "Other") }
        repo.applyBundledBank(solo + others)

        val first = repo.getPracticeQuiz(listOf("Solo"), "Mixed", QuizRepository.ENDLESS_COUNT, "Mixed")
        assertEquals(8, first.size)
        assertTrue(first.all { it.category == "Solo" })
        val used = first.mapTo(HashSet()) { QuestionPicker.keyOf(it) }
        val refill = repo.getPracticeQuiz(listOf("Solo"), "Mixed", QuizRepository.ENDLESS_COUNT, "Mixed", used)
        assertTrue("no fallback to other topics", refill.isEmpty())
    }

    @Test
    fun capitalQuestionsAreLeftOutOfTrueFalseQuizzes() = runBlocking {
        repo.applyBundledBank((0 until 30).map { tf("Statement $it is true?", "Topic") } + (0 until 30).map { q("MC $it?", "Topic") })
        val quiz = repo.getPracticeQuiz(listOf(Categories.CAPITAL_CITIES, "Topic"), "Mixed", 10, "True/False")
        assertEquals(10, quiz.size)
        assertTrue(quiz.all { QuestionPicker.isTrueFalse(it) })
        assertTrue(quiz.none { it.category == Categories.CAPITAL_CITIES })

        // Still included for multiple choice.
        val mc = repo.getPracticeQuiz(listOf(Categories.CAPITAL_CITIES, "Topic"), "Mixed", 10, "Multiple Choice")
        assertTrue(mc.any { it.category == Categories.CAPITAL_CITIES })
    }

    // --- A6: shorter than requested ---

    @Test
    fun shortQuizStartsWithWhatExistsAndSaysSo() = runBlocking {
        repo.applyBundledBank((0 until 6).map { tf("Fashion statement $it?", "Fashion & Design") } + (0 until 30).map { q("Fashion MC $it?", "Fashion & Design") })
        val quiz = repo.getPracticeQuiz(listOf("Fashion & Design"), "Mixed", 10, "True/False")
        assertEquals(6, quiz.size)
        assertEquals(
            "Only 6 True/False questions in Fashion & Design – playing 6",
            QuizNotices.forPractice(10, quiz, listOf("Fashion & Design"), "True/False")
        )
    }

    @Test
    fun noticesOnlyAppearWhenTheQuizIsDifferentFromTheRequest() {
        val full = (0 until 10).map { q("Full $it?", "Fashion & Design") }
        assertNull(QuizNotices.forPractice(10, full, listOf("Fashion & Design"), "Mixed"))
        assertNull(QuizNotices.forPractice(QuizRepository.ENDLESS_COUNT, full.take(3), listOf("Fashion & Design"), "Mixed"))
        assertEquals(
            "Only 1 question in your selected topics – playing 1",
            QuizNotices.forPractice(10, full.take(1), listOf("Fashion & Design", "Topic"), "Mixed")
        )
        // Nothing matched the topic, so the quiz fell back to other topics.
        assertEquals(
            "No True/False questions in Sports – playing mixed topics instead",
            QuizNotices.forPractice(10, full, listOf("Sports"), "True/False")
        )
        assertEquals("You've answered every question in Solo!", QuizNotices.endlessExhausted(listOf("Solo")))
    }

    // --- A9: the Daily is filed under the day it was started ---

    @Test
    fun dailySessionIsFiledUnderItsStartDate() = runBlocking {
        repo.initializeAppIfNeeded()
        val started = java.time.LocalDate.of(2026, 3, 1)
        repo.saveDailyResult(listOf(true, false), 42, started)
        repo.recordQuizSession(summary(List(6) { true }, SessionType.DAILY).copy(dailyDate = started))

        assertTrue(repo.isDailyPlayed(started))
        assertFalse(repo.isDailyPlayed(started.plusDays(1)))
        assertEquals("2026-03-01", repo.lastDailyResult()!!.date)
        assertNull(repo.todayDailyResult(started.plusDays(1)))
    }

    // --- A11: bundled bank parsing ---

    @Test
    fun realBundledBankParsesInFull() {
        val bank = repo.loadBundledQuestions()
        assertNotNull(bank)
        assertTrue("bank has ${bank!!.size} questions", bank.size > 14_000)
        assertTrue(bank.all { QuestionPicker.isPlayable(it) })
    }

    @Test
    fun oneBadEntryDoesNotLoseTheRestOfTheBank() {
        val json = """
            [
              {"text":"Good one?","options":["a","b"],"correctIndex":1,"category":"C","difficulty":"Easy","explanation":"e"},
              {"text":"Missing fields?"},
              {"text":"Wrong type?","options":"nope","correctIndex":0,"category":"C","difficulty":"easy","explanation":"e"},
              {"text":"Index out of range?","options":["a","b"],"correctIndex":5,"category":"C","difficulty":"easy","explanation":"e"},
              {"text":"Blank option?","options":["a"," "],"correctIndex":0,"category":"C","difficulty":"easy","explanation":"e"},
              {"text":" Good two? ","options":["x","y","z"],"correctIndex":2,"category":"C","difficulty":"hard","explanation":"e","imageUrl":null}
            ]
        """.trimIndent()
        val parsed = repo.parseBundledQuestions(Buffer().writeUtf8(json))
        assertEquals(listOf("Good one?", "Good two?"), parsed.map { it.text })
        assertEquals("easy", parsed[0].difficulty)
    }

    // --- A12: seen log ---

    @Test
    fun seenLogKeepsTheWholeBankAndStaysCompact() {
        assertTrue(SeenLog.DEFAULT_CAPACITY >= 20_000)
        val log = SeenLog()
        val random = kotlin.random.Random(1)
        var at = 1_700_000_000_000L
        val keys = (0 until 15_000).map { QuestionPicker.keyOf("Question $it ${random.nextInt()}") }
        keys.forEach { at += 4_000; log.mark(it, at) }
        assertEquals(15_000, log.size)

        val text = log.serialize()
        assertTrue("serialized to ${text.length} chars", text.length < 15_000 * 22)
        val restored = SeenLog.parse(text)
        assertEquals(log.snapshot(), restored.snapshot())
        log.remove(keys[0])
        assertNull(log.lastSeen(keys[0]))
    }

    @Test
    fun seenLogStillReadsTheOldFormat() {
        val restored = SeenLog.parse("100\tabc\n200\tdef\n")
        assertEquals(100L, restored.lastSeen("abc"))
        assertEquals(200L, restored.lastSeen("def"))
    }
}
