package com.light.lightemail.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.light.lightemail.MainActivity
import com.light.lightemail.R
import com.light.lightemail.data.EmailRepository
import com.light.lightemail.data.SyncEvent
import kotlinx.coroutines.flow.first

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

        val repository = EmailRepository(applicationContext)
        try {
            // Perform a full sync of the Inbox
            repository.syncEmails(email, password, host, "Inbox")
            
            val unreadEmails = repository.getEmails("Inbox").first().filter { !it.isRead }

            if (unreadEmails.isNotEmpty()) {
                val latestEmail = unreadEmails.first()
                val maxUid = unreadEmails.maxOf { it.uid }
                val lastSeenUid = prefs.getLong("last_seen_uid", -1L)
                val lastUnreadCount = prefs.getLong("last_unread_count", -1L)
                
                if (maxUid > lastSeenUid || unreadEmails.size.toLong() != lastUnreadCount) {
                    NotificationHelper.updateNotification(
                        applicationContext,
                        latestEmail.sender,
                        latestEmail.subject,
                        latestEmail.uid,
                        unreadEmails.size
                    )
                    
                    prefs.edit()
                        .putLong("last_seen_uid", maxOf(maxUid, lastSeenUid))
                        .putLong("last_unread_count", unreadEmails.size.toLong())
                        .apply()
                }
            } else {
                NotificationHelper.cancelNotification(applicationContext)
                prefs.edit().putLong("last_unread_count", 0).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        SyncEvent.trigger()
        return Result.success()
    }
}
