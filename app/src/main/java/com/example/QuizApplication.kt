package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
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
        ).build() // never destructive: future schema changes need a real Migration (see app/schemas)

        // 2. Initialize unified repository
        repository = QuizRepository(this, database.quizDao(), database = database)

        // 3. Kickstart database seeding and first-run defaults in the background
        applicationScope.launch {
            try {
                repository.initializeAppIfNeeded()
            } catch (e: Exception) {
                // Quiz builders call initializeAppIfNeeded() again, so a failed start-up is retried there.
                Log.e("QuizApplication", "Background initialisation failed", e)
            }
        }

        // 4b. The "Daily Reminder" switch also depends on the system notification setting, which the
        //    player can change outside the app, so re-check it whenever an activity comes to the front.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = repository.refreshReminderState()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // 4. Keep the opt-in daily reminder scheduled (e.g. after an app update).
        if (repository.isDailyReminderEnabled && DailyReminder.canNotify(this)) {
            DailyReminder.schedule(this)
        }
    }
}
