package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.repository.QuizPrefs
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * How long from [now] until the next [hour]:00 in [now]'s own time zone. Exactly [hour]:00 counts as
 * already passed, so the answer is for tomorrow. The target is built from the local date and time and
 * then resolved to an instant, so a day with a daylight-saving change is 23 or 25 hours long.
 */
fun nextReminderDelay(now: ZonedDateTime, hour: Int = DailyReminder.REMINDER_HOUR): Duration {
    var next = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(hour, 0).atZone(now.zone)
    return Duration.between(now, next)
}

/** Opt-in evening reminder to play the Daily Challenge, skipped on days it's already been played. */
object DailyReminder {
    const val REMINDER_HOUR = 18
    private const val CHANNEL_ID = "daily_reminder"
    // Old 24h periodic work (drifted with DST and time-zone changes); cancelled on every schedule().
    private const val LEGACY_PERIODIC_WORK_NAME = "daily_challenge_reminder"
    private const val WORK_NAME = "daily_challenge_reminder_next"
    private const val NOTIFICATION_ID = 4201

    /** Android 13+ needs the runtime notification permission; older versions don't. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Queues a one-off run at the next 18:00 local time; the worker queues the following one. */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(nextReminderDelay(ZonedDateTime.now()))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(WORK_NAME)
    }

    internal fun show(context: Context) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily Challenge reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder when you haven't played today's Daily Challenge"
            }
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_brainblitz)
            .setContentTitle("Today's Daily Challenge is waiting")
            .setContentText("Same 10 questions for everyone today. Keep your streak alive!")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}

class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = QuizPrefs(applicationContext)
        val playedToday = prefs.dailyPlayedDate == LocalDate.now().toString()
        if (!prefs.dailyReminderEnabled) return Result.success()
        if (!playedToday) DailyReminder.show(applicationContext)
        DailyReminder.schedule(applicationContext)
        return Result.success()
    }
}
