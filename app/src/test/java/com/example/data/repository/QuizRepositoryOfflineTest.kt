package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.QuizDao
import com.example.data.db.AppDatabase
import com.example.data.model.Question
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the repository against an in-memory Room database, so the real DAO queries are exercised. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuizRepositoryOfflineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: QuizDao
    private lateinit var prefs: QuizPrefs

    private val topics = (1..20).map { "Topic $it" }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.quizDao()
        prefs = QuizPrefs(context)
        prefs.bundledSeedVersion = QuizRepository.BUNDLED_SEED_VERSION // skip seeding: tests supply their own bank
    }

    @After
    fun tearDown() = db.close()

    private fun bank(size: Int) = (0 until size).map { i ->
        Question(
            text = "Question number $i?",
            optionsList = listOf("a$i", "b$i", "c$i", "d$i"),
            correctIndex = i % 4,
            category = topics[i % topics.size],
            difficulty = listOf("easy", "medium", "hard")[i % 3],
            explanation = "because $i"
        )
    }

    @Test
    fun practiceQuizWorksWithNoRemoteSource() = runBlocking {
        dao.insertQuestions(bank(200))
        val repo = QuizRepository(context, dao, prefs) // no remote source exists any more
        val quiz = repo.getPracticeQuiz(listOf("Topic 1", "Topic 2"), "Mixed", 10, "Mixed")
        assertEquals(10, quiz.size)
        assertTrue(quiz.all { it.category in setOf("Topic 1", "Topic 2") })
        assertEquals(10, quiz.map { QuestionPicker.keyOf(it) }.distinct().size)
    }

    @Test
    fun allTopicsQuizReturnsRequestedCountFromLargeBank() = runBlocking {
        dao.insertQuestions(bank(2_000))
        val repo = QuizRepository(context, dao, prefs)
        val quiz = repo.getPracticeQuiz(listOf(Categories.ALL_TOPICS), "Mixed", 30, "Multiple Choice")
        assertEquals(30, quiz.size)
        assertEquals(30, quiz.map { QuestionPicker.keyOf(it) }.distinct().size)
    }

    @Test
    fun daoQueriesLimitWhatTheyLoad() = runBlocking {
        dao.insertQuestions(bank(2_000))
        assertEquals(100, dao.getQuestionsByCategories(listOf("Topic 3")).size)
        assertEquals(800, dao.getRandomQuestions(800).size)
    }

    @Test
    fun firstLaunchSeedsBundledBankOnce() = runBlocking {
        prefs.bundledSeedVersion = 0
        val repo = QuizRepository(context, dao, prefs)
        repo.initializeAppIfNeeded()
        val count = dao.getQuestionsCount()
        assertTrue(count > 0)
        assertEquals(QuizRepository.BUNDLED_SEED_VERSION, prefs.bundledSeedVersion)
    }
}
