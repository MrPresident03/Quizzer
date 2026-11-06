package com.example.data.dao

import androidx.room.*
import com.example.data.model.Achievement
import com.example.data.model.Question
import com.example.data.model.QuizHistory
import com.example.data.model.UserStats
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    // --- QUESTIONS ---
    /** All questions in the given topics (quiz building never loads the whole table). */
    @Query("SELECT * FROM questions WHERE category IN (:topics)")
    suspend fun getQuestionsByCategories(topics: List<String>): List<Question>

    /** A random sample for all-topics quizzes. */
    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestions(limit: Int): List<Question>

    @Query("SELECT text FROM questions")
    suspend fun getAllQuestionTexts(): List<String>

    /** Every stored question, for the bundled-bank upsert (runs once per question-bank version). */
    @Query("SELECT * FROM questions")
    suspend fun getAllQuestions(): List<Question>

    @Update
    suspend fun updateQuestions(questions: List<Question>)

    /** Callers chunk [ids]: Android's SQLite allows only 999 bound variables per statement. */
    @Query("DELETE FROM questions WHERE id IN (:ids)")
    suspend fun deleteQuestionsByIds(ids: List<Int>)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun getQuestionsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Update
    suspend fun updateQuestion(question: Question)

    /** Single UPDATE instead of loading the whole table to change one row. */
    @Query("UPDATE questions SET answeredCount = answeredCount + 1, correctCount = correctCount + :correctIncrement WHERE text = :text")
    suspend fun incrementAnswerStats(text: String, correctIncrement: Int)

    @Query("UPDATE questions SET answeredCount = 0, correctCount = 0")
    suspend fun resetAnswerStats()

    /**
     * Removes the placeholder questions the old seeder generated ("What is 87 times 2?" filed under
     * Fashion, the same 4 History facts copied 6 times, etc.). Each pattern is paired with the old
     * generator's explanation wording so real questions can't match by accident.
     */
    @Query(
        """
        DELETE FROM questions WHERE
            (text LIKE 'What is the correct sum of % plus %?' AND explanation LIKE '% equals exactly %.')
         OR (text LIKE 'What is the correct periodic table chemical symbol for %?' AND explanation LIKE 'The chemical symbol for % is indeed %.')
         OR (text LIKE 'What is the official capital city of %?' AND explanation LIKE 'The capital of % is %.' AND explanation NOT LIKE '%. %')
         OR (text LIKE 'Who historical figure is celebrated as the %?')
         OR (text LIKE 'How many players are on the field/court for a single team in %?')
         OR (text LIKE 'What is the correct collective noun used for a grouping of %?')
         OR (text LIKE 'Question #% under category %: What is the mathematically solved result of % times 2?')
        """
    )
    suspend fun deleteLegacyGeneratedQuestions(): Int

    @Query("DELETE FROM questions")
    suspend fun clearQuestions()

    // --- USER STATS ---
    @Query("SELECT * FROM user_stats WHERE id = 1 LIMIT 1")
    fun getUserStatsFlow(): Flow<UserStats?>

    @Query("SELECT * FROM user_stats WHERE id = 1 LIMIT 1")
    suspend fun getUserStats(): UserStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserStats(stats: UserStats)

    /** Creates the default row only if there is none, so it can never overwrite real progress. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUserStatsIfMissing(stats: UserStats)

    // Partial updates: each writes only its own columns, so settings, theme and quiz results saved
    // at about the same time can't overwrite each other with a stale copy of the whole row.
    @Query("UPDATE user_stats SET activeThemeId = :themeId WHERE id = 1")
    suspend fun setActiveTheme(themeId: String)

    @Query("UPDATE user_stats SET isSoundEffectsEnabled = :sound, isHapticFeedbackEnabled = :haptic, fontSizePreference = :fontSize WHERE id = 1")
    suspend fun setToggles(sound: Boolean, haptic: Boolean, fontSize: String)

    @Query("UPDATE user_stats SET displayName = :name, avatarId = :avatarId WHERE id = 1")
    suspend fun setProfile(name: String, avatarId: String)

    @Query("UPDATE user_stats SET avatarId = :avatarId WHERE id = 1")
    suspend fun setAvatar(avatarId: String)

    @Query(
        """
        UPDATE user_stats SET
            totalQuestionsAnswered = totalQuestionsAnswered + :answered,
            correctAnswersCount = correctAnswersCount + :correct,
            xpPoints = xpPoints + :xp,
            currentLevel = MAX(currentLevel, :level)
        WHERE id = 1
        """
    )
    suspend fun addSessionTotals(answered: Int, correct: Int, xp: Int, level: Int)

    @Query("UPDATE user_stats SET currentStreak = :streak, hasStreakFreeze = :freezes, lastQuizDateStr = :date WHERE id = 1")
    suspend fun setStreak(streak: Int, freezes: Int, date: String)

    // --- QUIZ HISTORY ---
    @Query("SELECT * FROM quiz_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<QuizHistory>>

    @Query("SELECT COUNT(*) FROM quiz_history")
    suspend fun getHistoryCount(): Int

    /** Quizzes that count towards "N quizzes" achievements (not tiny Endless / Review runs). */
    @Query("SELECT COUNT(*) FROM quiz_history WHERE NOT (quizType IN ('Endless', 'Review') AND totalQuestions < :minAnswers)")
    suspend fun getCountedHistoryCount(minAnswers: Int): Int

    /** Lifetime XP earned (independent of XP spent in the shop). */
    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM quiz_history")
    suspend fun getLifetimeXp(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: QuizHistory)

    @Query("DELETE FROM quiz_history")
    suspend fun clearHistory()

    // --- ACHIEVEMENTS ---
    @Query("SELECT * FROM achievements")
    fun getAllAchievementsFlow(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements")
    suspend fun getAllAchievements(): List<Achievement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<Achievement>)

    /** Adds achievements that don't exist yet without wiping the unlock state of existing ones. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingAchievements(achievements: List<Achievement>)

    @Query("UPDATE achievements SET isUnlocked = 1, unlockedAt = :timestamp WHERE id = :id AND isUnlocked = 0")
    suspend fun unlockAchievement(id: String, timestamp: Long): Int

    @Query("DELETE FROM achievements WHERE id = :id")
    suspend fun deleteAchievement(id: String)

    @Query("UPDATE achievements SET isUnlocked = 0, unlockedAt = NULL")
    suspend fun resetAchievements()
}
