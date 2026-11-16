package com.ollie.iptv.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ollie.iptv.data.OllieDatabase
import com.ollie.iptv.data.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Recorder for direct HTTP streams and clear MPEG-TS HLS media playlists.
 * Encrypted/DRM HLS and fMP4/CMAF are deliberately rejected.
 */
class RecordingService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var currentOutput: File? = null
    @Volatile private var manualStopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            manualStopRequested = true
            running.set(false)
            connection?.disconnect()
            if (!running.get()) stopSelf()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL) ?: run { stopSelf(); return START_NOT_STICKY }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Ollie recording"
        val startAt = intent.getLongExtra(EXTRA_START_AT, 0L)
        val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 0)
        createChannel()
        startForeground(NOTIFICATION_ID, notification(if (startAt > System.currentTimeMillis()) "Scheduled: $title" else "Starting: $title"))
        if (running.compareAndSet(false, true)) {
            manualStopRequested = false
            currentOutput = null
            executor.execute {
                var error = ""
                var outputPath = ""
                val dao = OllieDatabase.get(this).dao()
                val recordingId = runBlocking(Dispatchers.IO) {
                    dao.insertRecording(
                        RecordingEntity(
                            title = title, streamUrl = url, localPath = "",
                            status = if (startAt > System.currentTimeMillis()) "SCHEDULED" else "RECORDING",
                            startedAt = if (startAt > System.currentTimeMillis()) 0L else System.currentTimeMillis(),
                            scheduledAt = startAt, durationMinutes = durationMinutes
                        )
                    )
                }
                try {
                    while (running.get() && startAt > System.currentTimeMillis()) {
                        val remaining = startAt - System.currentTimeMillis()
                        updateNotification("Starts in ${remaining / 60000} min: $title")
                        Thread.sleep(1000L.coerceAtMost(remaining.coerceAtLeast(1L)))
                    }
                    if (!running.get()) error("Recording cancelled before start.")
                    runBlocking(Dispatchers.IO) { dao.setRecordingStatus(recordingId, "RECORDING") }
                    val dir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OllieTV")
                    if (!dir.exists() && !dir.mkdirs()) error("Cannot create recording directory.")
                    val isHls = URL(url).path.endsWith(".m3u8", true)
                    val extension = if (isHls) "ts" else URL(url).path.substringAfterLast('.', "ts")
                        .takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) } ?: "ts"
                    val file = File(dir, "${System.currentTimeMillis()}_${safe(title)}.$extension")
                    currentOutput = file
                    val maxDuration = durationMinutes.coerceAtLeast(0) * 60_000L
                    if (isHls) {
                        HlsRecorder.record(
                            source = url,
                            output = file,
                            shouldContinue = { running.get() },
                            maxDurationMillis = maxDuration,
                            onProgress = ::updateNotification
                        )
                    } else {
                        recordDirect(url, file, maxDuration)
                    }
                    if (file.length() > 0L) outputPath = file.absolutePath
                    else file.delete()
                } catch (t: Throwable) {
                    if (manualStopRequested) {
                        outputPath = currentOutput?.takeIf { it.exists() && it.length() > 0L }?.absolutePath.orEmpty()
                    } else {
                        error = t.message ?: "Recording failed"
                    }
                } finally {
                    runBlocking(Dispatchers.IO) {
                        dao.updateRecording(
                            recordingId,
                            if (error.isBlank() && outputPath.isNotBlank()) "COMPLETED" else "FAILED",
                            System.currentTimeMillis(),
                            outputPath,
                            error
                        )
                    }
                    connection?.disconnect()
                    running.set(false)
                    currentOutput = null
                    updateNotification(if (error.isBlank()) "Saved: $title" else "Failed: $error")
                    stopForeground(false)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun recordDirect(url: String, file: File, maxDurationMillis: Long) {
        val started = System.currentTimeMillis()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("User-Agent", "OllieTV/0.4")
        }
        connection = conn
        conn.connect()
        if (conn.responseCode !in 200..299) error("Stream HTTP ${conn.responseCode}")
        FileOutputStream(file).use { output ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (running.get() && (maxDurationMillis <= 0 || System.currentTimeMillis() - started < maxDurationMillis)) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Ollie recordings", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Ollie TV recorder")
            .setContentText(text)
            .setOngoing(running.get())
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun safe(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]+"), "_").take(50)

    override fun onDestroy() {
        running.set(false)
        connection?.disconnect()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.ollie.iptv.STOP_RECORDING"
        const val EXTRA_URL = "record_url"
        const val EXTRA_TITLE = "record_title"
        const val EXTRA_START_AT = "record_start_at"
        const val EXTRA_DURATION_MINUTES = "record_duration_minutes"
        const val CHANNEL = "ollie_recordings"
        const val NOTIFICATION_ID = 4301
    }
}
