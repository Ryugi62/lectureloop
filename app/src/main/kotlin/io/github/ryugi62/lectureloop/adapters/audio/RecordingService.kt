package io.github.ryugi62.lectureloop.adapters.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import io.github.ryugi62.lectureloop.MainActivity
import io.github.ryugi62.lectureloop.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** What the record screen shows. */
sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val file: File, val startedAtElapsed: Long, val level: Float) : RecorderState
    data class Finished(val file: File, val seconds: Int) : RecorderState
    data class Error(val message: String) : RecorderState
}

/**
 * A 75-minute lecture outlives the screen, so recording runs in a microphone foreground service.
 * AAC mono 16 kHz at 32 kbps ≈ 14 MB/hour — small enough to send inline for most lectures.
 */
class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop(keep = true)
            ACTION_CANCEL -> stop(keep = false)
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (recorder != null) return
        startForegroundCompat()
        val out = File(filesDir, "audio").apply { mkdirs() }.let { File(it, "rec-${System.currentTimeMillis()}.m4a") }
        try {
            recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
            file = out
            started = SystemClock.elapsedRealtime()
            state.value = RecorderState.Recording(out, started, 0f)
            ticker.post(tick)
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            state.value = RecorderState.Error(e.message ?: "Microphone unavailable")
            stopSelf()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            val f = file ?: return
            val level = (r.maxAmplitude / 32767f).coerceIn(0f, 1f)
            state.value = RecorderState.Recording(f, started, level)
            ticker.postDelayed(this, 120)
        }
    }

    private fun stop(keep: Boolean) {
        ticker.removeCallbacks(tick)
        val r = recorder
        val f = file
        recorder = null
        if (r != null && f != null) {
            val seconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
            try {
                r.stop()
                if (keep) {
                    state.value = RecorderState.Finished(f, seconds)
                } else {
                    f.delete()
                    state.value = RecorderState.Idle
                }
            } catch (e: RuntimeException) {
                f.delete()
                state.value = if (keep) RecorderState.Error("Recording was too short") else RecorderState.Idle
            } finally {
                r.release()
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Recording your lecture")
            .setContentText("Tap to open LectureLoop")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_CANCEL = "cancel"

        val state = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val stateFlow: StateFlow<RecorderState> get() = state

        fun start(context: Context) {
            state.value = RecorderState.Idle
            context.startForegroundService(Intent(context, RecordingService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }

        /** Stop and throw the audio away (the student closed the screen). */
        fun cancel(context: Context) {
            if (state.value is RecorderState.Recording) {
                context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_CANCEL))
            }
            state.value = RecorderState.Idle
        }

        fun reset() { state.value = RecorderState.Idle }
    }
}
