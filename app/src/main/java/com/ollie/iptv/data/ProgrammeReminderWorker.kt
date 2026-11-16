package com.ollie.iptv.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProgrammeReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_REMINDER_ID, -1L)
        if (id <= 0L) return Result.failure()
        val dao = OllieDatabase.get(applicationContext).dao()
        val reminder = dao.programmeReminder(id) ?: return Result.success()
        if (reminder.status != "SCHEDULED") return Result.success()
        if (System.currentTimeMillis() >= reminder.programmeStartUtc) {
            dao.updateProgrammeReminderStatus(id, "EXPIRED")
            return Result.success()
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            dao.updateProgrammeReminderStatus(id, "PERMISSION_DENIED")
            return Result.success()
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Programme reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val channelName = dao.stream(reminder.streamId)?.name ?: "Ollie TV"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Starting in ${reminder.minutesBefore} minutes")
            .setContentText("$channelName • ${reminder.programmeTitle}")
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(id.toInt(), notification)
        dao.updateProgrammeReminderStatus(id, "DELIVERED")
        return Result.success()
    }
    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val CHANNEL_ID = "ollie_programme_reminders"
    }
}
