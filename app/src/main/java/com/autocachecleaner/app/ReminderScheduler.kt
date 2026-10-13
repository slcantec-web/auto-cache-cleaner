package com.autocachecleaner.app

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME = "cache_reminder_work"
    private const val PREFS = "app_settings"
    const val KEY_INTERVAL = "reminder_interval_days"
    const val DEFAULT_DAYS = 7L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSavedInterval(context: Context): Boolean = prefs(context).contains(KEY_INTERVAL)

    fun currentInterval(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL, DEFAULT_DAYS)

    /** Sets the reminder interval in days. 0 (or less) disables reminders. */
    fun setInterval(context: Context, days: Long) {
        if (days <= 0) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } else {
            val request = PeriodicWorkRequestBuilder<CacheReminderWorker>(days, TimeUnit.DAYS)
                .setInitialDelay(days, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
        prefs(context).edit().putLong(KEY_INTERVAL, maxOf(days, 0L)).apply()
    }
}
