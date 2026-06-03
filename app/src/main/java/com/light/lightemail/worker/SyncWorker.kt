package com.light.lightemail.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.light.lightemail.MainActivity
import com.light.lightemail.R
import com.light.lightemail.data.ImapManager
import com.light.lightemail.data.SyncEvent

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = "push_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.push_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.push_service_title))
            .setContentText(applicationContext.getString(R.string.push_service_desc))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return androidx.work.ForegroundInfo(1001, notification)
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null) ?: return Result.success()
        val password = prefs.getString("password", null) ?: return Result.success()
        val host = prefs.getString("host", null) ?: return Result.success()

        val imapManager = ImapManager()
        try {
            val unreadEmails = imapManager.fetchUnreadEmails(
                email = email,
                password = password,
                host = host,
                limit = 10,
                noSubjectString = applicationContext.getString(R.string.no_subject),
                unknownSenderString = applicationContext.getString(R.string.unknown_sender)
            )

            if (unreadEmails.isNotEmpty()) {
                val latestEmail = unreadEmails.first()
                showNotification(latestEmail.sender, latestEmail.subject, latestEmail.uid, unreadEmails.size)
                
                val maxUid = unreadEmails.maxOf { it.uid }
                val lastSeenUid = prefs.getLong("last_seen_uid", -1L)
                if (maxUid > lastSeenUid) {
                    prefs.edit().putLong("last_seen_uid", maxUid).apply()
                }
            } else {
                // No unread emails found (all read or deleted)
                cancelNotification()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        // Trigger UI refresh if app is in foreground
        SyncEvent.trigger()

        return Result.success()
    }

    private fun showNotification(sender: String, subject: String, uid: Long, count: Int = 1) {
        val channelId = "new_email_channel_v2"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.new_emails_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.new_emails_channel_desc)
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_EMAIL_UID", uid)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count > 1) {
            applicationContext.getString(R.string.new_emails_count, count)
        } else {
            applicationContext.getString(R.string.new_email_from, sender)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(subject)
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun cancelNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)
    }
}
