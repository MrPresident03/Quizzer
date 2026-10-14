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
    @Query("SELECT * FROM questions")
    fun getAllQuestionsFlow(): Flow<List<Question>>

    @Query("SELECT * FROM questions")
    suspend fun getAllQuestions(): List<Question>

    @Query("SELECT text FROM questions")
    suspend fun getAllQuestionTexts(): List<String>

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

    // --- QUIZ HISTORY ---
    @Query("SELECT * FROM quiz_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<QuizHistory>>

    @Query("SELECT COUNT(*) FROM quiz_history")
    suspend fun getHistoryCount(): Int

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
