package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.repository.QuizRepository
import com.example.notifications.DailyReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuizApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: QuizRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "brainblitz_database"
        ).fallbackToDestructiveMigration().build()

        // 2. Initialize unified repository
        repository = QuizRepository(this, database.quizDao())

        // 3. Kickstart database seeding and first-run defaults in the background
        applicationScope.launch {
            repository.initializeAppIfNeeded()
        }

        // 4. Keep the opt-in daily reminder scheduled (e.g. after an app update).
        if (repository.isDailyReminderEnabled && DailyReminder.canNotify(this)) {
            DailyReminder.schedule(this)
        }
    }
}
