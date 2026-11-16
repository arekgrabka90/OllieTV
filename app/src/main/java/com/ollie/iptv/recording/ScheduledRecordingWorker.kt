package com.ollie.iptv.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ollie.iptv.data.OllieDatabase
import com.ollie.iptv.data.CredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * WorkManager-backed scheduled recording. The request is persisted by WorkManager,
 * so it survives process death and device restarts according to WorkManager policy.
 */
class ScheduledRecordingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getLong(KEY_ID, -1L)
        val title = inputData.getString(KEY_TITLE) ?: "Scheduled recording"
        val encryptedUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val url = CredentialVault.decrypt(encryptedUrl)
        val durationMinutes = inputData.getInt(KEY_DURATION, 60).coerceIn(1, 1440)
        if (id < 0) return@withContext Result.failure()

        val dao = OllieDatabase.get(applicationContext).dao()
        createNotificationChannel()
        setForeground(createForegroundInfo("Recording: $title"))
        dao.setRecordingStatus(id, "RECORDING", System.currentTimeMillis())

        var outputPath = ""
        var errorMessage = ""
        try {
            val directory = File(
                applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
                "OllieTV"
            )
            if (!directory.exists() && !directory.mkdirs()) error("Cannot create recording directory.")
            val hls = URL(url).path.endsWith(".m3u8", true)
            val extension = if (hls) "ts" else URL(url).path.substringAfterLast('.', "ts")
                .takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) } ?: "ts"
            val file = File(directory, "${System.currentTimeMillis()}_${safe(title)}.$extension")
            if (hls) {
                HlsRecorder.record(
                    source = url,
                    output = file,
                    shouldContinue = { !isStopped },
                    maxDurationMillis = durationMinutes * 60_000L,
                    onProgress = { message ->
                        setForegroundAsync(createForegroundInfo(message))
                    }
                )
            } else {
                recordDirect(url, file, durationMinutes * 60_000L)
            }
            if (file.exists() && file.length() > 0L) outputPath = file.absolutePath
            else error("Recording produced an empty file.")
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Scheduled recording failed."
        } finally {
            dao.updateRecording(
                id,
                if (errorMessage.isBlank() && outputPath.isNotBlank()) "COMPLETED" else "FAILED",
                System.currentTimeMillis(),
                outputPath,
                errorMessage
            )
        }
        if (errorMessage.isBlank() && outputPath.isNotBlank()) Result.success() else Result.failure()
    }

    private fun recordDirect(url: String, file: File, maxDuration: Long) {
        val start = System.currentTimeMillis()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "OllieTV/0.4")
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("Stream HTTP ${connection.responseCode}")
            FileOutputStream(file).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (!isStopped && System.currentTimeMillis() - start < maxDuration) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL, "Ollie scheduled recordings", NotificationManager.IMPORTANCE_LOW)
                )
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Ollie TV")
            .setContentText(message)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun safe(value: String) = value.replace(Regex("[^a-zA-Z0-9._-]+"), "_").take(50)

    companion object {
        const val KEY_ID = "recording_id"
        const val KEY_TITLE = "recording_title"
        const val KEY_URL = "recording_url"
        const val KEY_DURATION = "recording_duration"
        const val CHANNEL = "ollie_scheduled_recordings"
        const val NOTIFICATION_ID = 4302
    }
}
