package com.example.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.QuizDao
import com.example.data.db.AppDatabase
import com.example.data.model.Question
import com.example.data.repository.DailyChallenge
import com.example.data.repository.PracticeSettings
import com.example.data.repository.QuizPrefs
import com.example.data.repository.QuizRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import java.time.LocalDate

/**
 * Drives the real ViewModel against an in-memory database. The ViewModel's coroutines run on a
 * test dispatcher with virtual time (so 1-second timer ticks are instant), while the database work
 * runs on real threads, which [settle] waits for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuizViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scheduler get() = dispatcher.scheduler

    private lateinit var db: AppDatabase
    private lateinit var dao: QuizDao
    private lateinit var repo: QuizRepository
    private lateinit var vm: QuizViewModel
    private var today = LocalDate.of(2026, 11, 5)
    private var clockMs = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.quizDao()
        val prefs = QuizPrefs(app)
        prefs.bundledSeedVersion = QuizRepository.BUNDLED_SEED_VERSION // tests supply their own bank
        repo = QuizRepository(app, dao, prefs, db)
        vm = QuizViewModel(app, repo, today = { today }, elapsedClock = { clockMs })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun bank(topic: String, count: Int, trueFalse: Boolean = false) = (0 until count).map { i ->
        Question(
            text = "$topic question $i${if (trueFalse) " is true" else ""}?",
            optionsList = if (trueFalse) listOf("True", "False") else listOf("a$i", "b$i", "c$i", "d$i"),
            correctIndex = 0,
            category = topic,
            difficulty = "easy",
            explanation = "because"
        )
    }

    private fun seed(vararg questions: List<Question>) = runBlocking {
        repo.applyBundledBank(questions.toList().flatten())
        Unit
    }

    /** Waits (in real time) for background work, letting the ViewModel's own coroutines run. */
    private fun settle(what: String = "condition", condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (true) {
            scheduler.runCurrent()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            Thread.sleep(5)
        }
    }

    /** Moves virtual time forward and runs everything that became due. */
    private fun tick(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun startPractice(topic: String, count: Int, format: String = "Multiple Choice") {
        vm.startPracticeQuiz(listOf(topic), "Mixed", count, format)
        settle("questions to load") { vm.activeQuestions.value.isNotEmpty() || vm.loadError.value != null }
    }

    private fun answerCurrent(correct: Boolean = true) {
        val q = vm.activeQuestions.value[vm.currentQuestionIndex.value]
        val choice = if (correct) q.correctIndex else (q.correctIndex + 1) % q.optionsList.size
        vm.selectOption(choice)
    }

    /** Answers questions until the quiz ends (or [limit] answers were given). */
    private fun playToEnd(correct: Boolean = true, limit: Int = 100) {
        var given = 0
        while (!vm.quizFinished.value && given++ < limit) {
            val index = vm.currentQuestionIndex.value
            answerCurrent(correct)
            tick(6_000)
            settle("next question") { vm.quizFinished.value || vm.currentQuestionIndex.value != index }
        }
        assertTrue("quiz should have ended", vm.quizFinished.value)
        settle("session saved") { runBlocking { dao.getHistoryCount() } >= 1 }
    }

    // --- A2: timer pause / resume ---

    @Test
    fun timerPausesAndResumesKeepingTheRemainingSeconds() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        val full = PracticeSettings.QUESTION_SECONDS
        assertEquals(full, vm.timeLeft.value)

        tick(3_000)
        assertEquals(full - 3, vm.timeLeft.value)

        vm.pauseTimer()
        tick(20_000)
        assertEquals("paused: no countdown", full - 3, vm.timeLeft.value)

        vm.resumeTimer()
        tick(2_000)
        assertEquals("resumed from where it stopped", full - 5, vm.timeLeft.value)
    }

    @Test
    fun pausingTwiceAndResumingOnceResumes() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        vm.pauseTimer()
        vm.pauseTimer() // e.g. quit dialog open and app backgrounded
        vm.resumeTimer()
        val before = vm.timeLeft.value
        tick(2_000)
        assertEquals(before - 2, vm.timeLeft.value)
    }

    @Test
    fun resumeDoesNothingOnceTheQuestionIsAnswered() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        tick(1_000)
        answerCurrent(correct = false) // wrong answers stay on screen for 6 s
        val left = vm.timeLeft.value
        vm.pauseTimer()
        vm.resumeTimer()
        tick(3_000)
        assertEquals("no timer restarted for an answered question", left, vm.timeLeft.value)
        assertEquals(1, vm.quizAnswersList.size)
    }

    @Test
    fun aQuestionThatAppearsWhilePausedWaitsForResume() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        answerCurrent(correct = true) // advances after 2.5 s
        vm.pauseTimer() // app goes to the background during the reveal
        tick(3_000)
        assertEquals(1, vm.currentQuestionIndex.value)
        assertEquals(PracticeSettings.QUESTION_SECONDS, vm.timeLeft.value)
        tick(10_000)
        assertEquals("timer waits while paused", PracticeSettings.QUESTION_SECONDS, vm.timeLeft.value)
        vm.resumeTimer()
        tick(2_000)
        assertEquals(PracticeSettings.QUESTION_SECONDS - 2, vm.timeLeft.value)
    }

    @Test
    fun doubleTapOnAnAnswerCountsOnce() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        val q = vm.activeQuestions.value[0]
        vm.selectOption(q.correctIndex)
        vm.selectOption((q.correctIndex + 1) % 4)
        vm.selectOption(q.correctIndex)
        assertEquals(listOf(true), vm.quizAnswersList)
        assertEquals(q.correctIndex, vm.currentSelectedOption.value)
    }

    // --- A12: Speed Demon uses real elapsed time ---

    @Test
    fun speedStreakUsesRealTimeNotTheTimerValue() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        vm.useLifelineExtraTime() // pushes timeLeft above the maximum
        clockMs += 25_000 // 25 real seconds spent thinking
        answerCurrent(correct = true)
        // Not fast, even though "max - timeLeft" would have said 0 seconds spent. Finish the quiz and
        // check no Speed Demon progress was recorded for that answer.
        tick(3_000)
        for (i in 1 until 10) {
            clockMs += 1_000
            answerCurrent(correct = true)
            tick(3_000)
            settle("next") { vm.quizFinished.value || vm.currentQuestionIndex.value == i + 1 }
        }
        settle("finished") { vm.quizFinished.value }
        settle("saved") { runBlocking { dao.getHistoryCount() } >= 1 }
        // 9 fast answers in a row after one slow one: the best streak is 9, so no 10-streak badge.
        assertFalse(runBlocking { dao.getAllAchievements() }.first { it.id == "speed_demon" }.isUnlocked)
    }

    // --- A5: Endless ends when the topic runs out ---

    @Test
    fun endlessRunEndsWhenTheTopicIsExhaustedAndSaysSo() {
        seed(bank("Solo", 8), bank("Other", 40))
        vm.startPracticeQuiz(listOf("Solo"), "Mixed", QuizRepository.ENDLESS_COUNT, "Mixed")
        settle("questions to load") { vm.activeQuestions.value.isNotEmpty() }

        playToEnd()

        assertEquals(8, vm.quizAnswersList.size)
        assertTrue("never drifted into other topics", vm.activeQuestions.value.all { it.category == "Solo" })
        assertEquals("You've answered every question in Solo!", vm.endMessage.value)
    }

    @Test
    fun endlessRunStoppedByThePlayerHasNoExhaustedMessage() {
        seed(bank("Big", 60))
        vm.startPracticeQuiz(listOf("Big"), "Mixed", QuizRepository.ENDLESS_COUNT, "Mixed")
        settle("questions to load") { vm.activeQuestions.value.isNotEmpty() }
        answerCurrent()
        tick(3_000)
        assertTrue(vm.quitQuiz())
        settle("finished") { vm.quizFinished.value }
        assertNull(vm.endMessage.value)
    }

    // --- A6: notice when the quiz is shorter than requested ---

    @Test
    fun shortQuizShowsADismissibleNotice() {
        seed(bank("Fashion & Design", 6, trueFalse = true), bank("Fashion & Design MC", 5))
        startPractice("Fashion & Design", 10, format = "True/False")
        assertEquals(6, vm.activeQuestions.value.size)
        assertEquals("Only 6 True/False questions in Fashion & Design – playing 6", vm.notice.value)
        vm.dismissNotice()
        assertNull(vm.notice.value)
    }

    @Test
    fun fullLengthQuizHasNoNotice() {
        seed(bank("Topic", 30))
        startPractice("Topic", 10)
        assertEquals(10, vm.activeQuestions.value.size)
        assertNull(vm.notice.value)
    }

    // --- A8: perfect rule and short Endless runs ---

    @Test
    fun shortQuizIsNeverPerfectButFiveQuestionsAre() {
        seed(bank("Topic", 40))
        startPractice("Topic", 3)
        playToEnd()
        assertFalse(unlocked("first_perfect"))
        assertTrue(unlocked("first_quiz"))

        startPractice("Topic", 5)
        playToEnd(limit = 5)
        settle("second session saved") { runBlocking { dao.getHistoryCount() } >= 2 }
        assertTrue(unlocked("first_perfect"))
    }

    @Test
    fun tinyEndlessRunDoesNotCountTowardStreakOrQuizAchievements() {
        seed(bank("Big", 60))
        vm.startPracticeQuiz(listOf("Big"), "Mixed", QuizRepository.ENDLESS_COUNT, "Mixed")
        settle("questions to load") { vm.activeQuestions.value.isNotEmpty() }
        repeat(3) {
            val index = vm.currentQuestionIndex.value
            answerCurrent()
            tick(3_000)
            settle("next question") { vm.currentQuestionIndex.value != index }
        }
        assertTrue(vm.quitQuiz())
        settle("saved") { runBlocking { dao.getHistoryCount() } >= 1 }

        val stats = runBlocking { dao.getUserStats() }!!
        assertEquals(0, stats.currentStreak)
        assertEquals(3, stats.totalQuestionsAnswered) // the answers themselves still count
        assertFalse(unlocked("first_quiz"))
        assertFalse(unlocked("first_perfect"))
    }

    private fun unlocked(id: String) = runBlocking { dao.getAllAchievements() }.first { it.id == id }.isUnlocked

    // --- A9: Daily filed under the day it started ---

    @Test
    fun dailyThatFinishesAfterMidnightIsFiledUnderItsStartDay() {
        val started = today
        vm.startDailyChallenge()
        settle("daily to load") { vm.activeQuestions.value.isNotEmpty() || vm.loadError.value != null }
        assertNull(vm.loadError.value)

        today = started.plusDays(1) // midnight passes mid-quiz
        playToEnd()

        assertTrue(repo.isDailyPlayed(started))
        assertFalse(repo.isDailyPlayed(started.plusDays(1)))
        assertEquals(started.toString(), repo.lastDailyResult()!!.date)
        assertFalse("the new day's Daily is still available", vm.dailyPlayedToday.value)
        val share = vm.dailyShareText()
        assertNotNull(share)
        assertTrue(share!!.contains("#${DailyChallenge.number(started)}"))
    }

    @Test
    fun quittingTheDailyAfterAnsweringSavesTheScoreSoFar() {
        vm.startDailyChallenge()
        settle("daily to load") { vm.activeQuestions.value.isNotEmpty() }
        answerCurrent()
        tick(3_000)
        assertTrue(vm.quitQuiz())
        settle("daily saved") { runBlocking { dao.getHistoryCount() } >= 1 }
        assertTrue(repo.isDailyPlayed(today))
        assertEquals(1, repo.lastDailyResult()!!.answers.size)
        assertFalse("an abandoned Daily is never perfect", unlocked("daily_champ"))
    }

    @Test
    fun quittingTheDailyBeforeAnsweringKeepsTheAttempt() {
        vm.startDailyChallenge()
        settle("daily to load") { vm.activeQuestions.value.isNotEmpty() }
        assertFalse(vm.quitQuiz())
        assertFalse(repo.isDailyPlayed(today))
    }
}
