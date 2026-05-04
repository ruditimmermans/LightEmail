/*
 * This file is part of LightEmail.
 *
 * LightEmail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LightEmail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with LightEmail.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2018-2020, Distopico (dystopia project) <distopico@riseup.net> and contributors
 */
package org.dystopia.email.util

import android.app.NotificationManager
import android.os.Build
import android.os.PowerManager
import android.app.AlarmManager
import android.net.ConnectivityManager
import android.app.job.JobScheduler
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import org.dystopia.email.BuildConfig

object CompatibilityHelper {
    @JvmStatic
    fun getNotificationManger(context: Context): NotificationManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(NotificationManager::class.java)
        } else context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @JvmStatic
    fun getPowerManager(context: Context): PowerManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(PowerManager::class.java)
        } else context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @JvmStatic
    fun getAlarmManager(context: Context): AlarmManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(AlarmManager::class.java)
        } else context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @JvmStatic
    fun getConnectivityManager(context: Context): ConnectivityManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(ConnectivityManager::class.java)
        } else context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @JvmStatic
    fun getInputMethodManager(context: Context): InputMethodManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(InputMethodManager::class.java)
        } else context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    @JvmStatic
    fun getJobScheduler(context: Context): JobScheduler {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(JobScheduler::class.java)
        } else {
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        }
    }

    @JvmStatic
    fun getClipboardManager(context: Context): ClipboardManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(ClipboardManager::class.java)
        } else context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @JvmStatic
    fun isIgnoringOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getPowerManager(context)
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
            }
        }
        return true
    }

    @JvmStatic
    fun setAndAllowWhileIdle(
        alarmManager: AlarmManager,
        type: Int,
        triggerAtMillis: Long,
        operation: PendingIntent?
    ) {
        if (operation == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(type, triggerAtMillis, operation)
            return
        }
        alarmManager.setExact(type, triggerAtMillis, operation)
    }
}
