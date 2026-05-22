package com.light.lightemail.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.light.lightemail.R
import com.light.lightemail.data.ImapManager
import com.light.lightemail.worker.SyncWorker
import kotlinx.coroutines.*

class EmailPushService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var idleJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startIdleListening()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "push_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.push_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.push_service_title))
            .setContentText(getString(R.string.push_service_desc))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun startIdleListening() {
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            val prefs = getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("email", null)
            val password = prefs.getString("password", null)
            val host = prefs.getString("host", null)

            if (email != null && password != null && host != null) {
                val imapManager = ImapManager()
                while (isActive) {
                    try {
                        imapManager.startIdle(email, password, host) {
                            // Trigger SyncWorker to fetch details and notify
                            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                                .build()
                            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                                "email_sync_push",
                                ExistingWorkPolicy.REPLACE,
                                syncRequest
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Use a shorter delay if it was a timeout, or longer if it's a real error
                        delay(60000) // 1 minute
                    }
                }
            } else {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
