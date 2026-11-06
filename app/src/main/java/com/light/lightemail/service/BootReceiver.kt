package com.light.lightemail.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.light.lightemail.worker.SyncWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            try {
                val prefs = context.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)
                val startOnBoot = prefs.getBoolean("start_on_boot", true)
                val email = prefs.getString("email", null)
                val password = prefs.getString("password", null)
                val host = prefs.getString("host", null)

                if (startOnBoot && !email.isNullOrEmpty() && !password.isNullOrEmpty() && !host.isNullOrEmpty()) {
                    // 1. Attempt to start foreground EmailPushService
                    try {
                        val serviceIntent = Intent(context, EmailPushService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. Schedule immediate WorkManager sync as boot fallback
                    try {
                        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()
                            )
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "email_sync_boot",
                            ExistingWorkPolicy.REPLACE,
                            syncRequest
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 3. Schedule periodic WorkManager sync to survive OS task kills
                    try {
                        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                            15, TimeUnit.MINUTES
                        ).setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        ).build()

                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "email_periodic_sync",
                            ExistingPeriodicWorkPolicy.KEEP,
                            periodicSyncRequest
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
