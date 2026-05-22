package com.light.lightemail.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.light.lightemail.MainActivity
import com.light.lightemail.R
import com.light.lightemail.data.ImapManager

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null) ?: return Result.success()
        val password = prefs.getString("password", null) ?: return Result.success()
        val host = prefs.getString("host", null) ?: return Result.success()

        val imapManager = ImapManager()
        val emails = imapManager.fetchEmails(
            email = email,
            password = password,
            host = host,
            limit = 1,
            noSubjectString = applicationContext.getString(R.string.no_subject),
            unknownSenderString = applicationContext.getString(R.string.unknown_sender),
            errorReadingContentString = applicationContext.getString(R.string.error_reading_content),
            fetchContent = false // Don't fetch content in background to save battery/data
        )

        if (emails.isNotEmpty()) {
            val latestEmail = emails.first()
            val lastSeenUid = prefs.getLong("last_seen_uid", -1L)
            
            if (latestEmail.uid > lastSeenUid) {
                showNotification(latestEmail.sender, latestEmail.subject)
                // Use commit() instead of apply() to ensure UID is saved immediately in the worker
                prefs.edit().putLong("last_seen_uid", latestEmail.uid).commit()
            }
        }

        // Reschedule if interval < 15 minutes and push is not enabled
        val syncInterval = prefs.getInt("sync_interval", 15)
        val enablePush = prefs.getBoolean("enable_push", false)
        
        if (syncInterval < 15 && !enablePush) {
            val workManager = androidx.work.WorkManager.getInstance(applicationContext)
            
            // Check battery level manually for aggressive rescheduling
            val batteryStatus: android.content.Intent? = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                applicationContext.registerReceiver(null, ifilter)
            }
            val status: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging: Boolean = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == android.os.BatteryManager.BATTERY_STATUS_FULL
            
            // If battery is low and not charging, force a longer interval (at least 15 min)
            val effectiveInterval = if (!isCharging && syncInterval < 15) {
                val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = level * 100 / scale.toFloat()
                if (batteryPct < 20) 15 else syncInterval
            } else {
                syncInterval
            }

            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(effectiveInterval.toLong(), java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .build()
            
            workManager.enqueueUniqueWork(
                "email_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }

        return Result.success()
    }

    private fun showNotification(sender: String, subject: String) {
        val channelId = "new_email_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(channelId, "New Emails", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("New Email from $sender")
            .setContentText(subject)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}
