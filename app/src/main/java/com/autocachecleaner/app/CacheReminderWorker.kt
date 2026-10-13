package com.autocachecleaner.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs once per reminder interval. Shows the "clear your cache" reminder and, while it is
 * awake anyway, checks whether a newer app version has been released.
 */
class CacheReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        showReminderNotification()
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun showReminderNotification() {
        val update = UpdateChecker.fetchLatest()?.takeIf { UpdateChecker.isNewer(it) }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName("Cache Clear Reminders")
                .setDescription("Reminds you to clear cache and tells you about app updates.")
                .build()
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (update != null) putExtra(MainActivity.EXTRA_CHECK_UPDATE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String
        if (update != null) {
            title = "Update available: v${update.versionName}"
            text = "Tap to open Auto Cache Cleaner and download the new version."
        } else {
            title = "Time to Clear App Cache!"
            text = "It's been a while. Tap here to open Auto Cache Cleaner and free up storage."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clean)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "cache_reminder_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
