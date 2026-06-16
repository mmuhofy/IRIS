package com.iris.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.iris.assistant.R
import com.iris.assistant.ui.MainActivity
import com.iris.assistant.util.Constants
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val MAX_RESTART_ATTEMPTS = 3

        const val ACTION_WAKE_WORD_DETECTED = "com.iris.assistant.WAKE_WORD_DETECTED"
        const val ACTION_START              = "com.iris.assistant.wake_word.START"
        const val ACTION_STOP               = "com.iris.assistant.wake_word.STOP"
        const val ACTION_PAUSE              = "com.iris.assistant.wake_word.PAUSE"
        const val ACTION_RESUME             = "com.iris.assistant.wake_word.RESUME"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var detectionJob: Job? = null
    private var engine: WakeWordEngine? = null
    private var restartAttempts = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID_WAKE, buildNotification())
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START  -> startDetection()
            ACTION_PAUSE  -> pauseDetection()
            ACTION_RESUME -> startDetection()
            ACTION_STOP   -> stopSelf()
            else          -> Log.w(TAG, "onStartCommand: unknown action — ignoring")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pauseDetection()
        serviceScope.cancel()
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun startDetection() {
        if (engine != null) {
            Log.w(TAG, "startDetection: engine already running — ignoring")
            return
        }

        Log.d(TAG, "startDetection: creating WakeWordEngine")

        val models = listOf(
            WakeWordModel(
                name      = Constants.WAKE_WORD_MODEL_NAME,
                modelPath = Constants.WAKE_WORD_MODEL_FILE,
                threshold = Constants.WAKE_WORD_THRESHOLD
            )
        )

        engine = WakeWordEngine(
            context             = applicationContext,
            models              = models,
            detectionMode       = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = Constants.WAKE_WORD_COOLDOWN_MS
        ).also { it.start() }

        val currentEngine = engine ?: return

        detectionJob = serviceScope.launch {
            try {
                currentEngine.detections.collect { detection ->
                    Log.d(TAG, "Detected: ${detection.model.name} score=${detection.score}")
                    broadcastDetected()
                }
            } catch (e: Exception) {
                Log.w(TAG, "detectionJob failed: ${e.message}")
                // Release the crashed engine before nulling ref
                currentEngine.release()
                engine = null
                restartAttempts++
                if (restartAttempts <= MAX_RESTART_ATTEMPTS) {
                    Log.d(TAG, "restarting in 500ms (attempt $restartAttempts/$MAX_RESTART_ATTEMPTS)")
                    delay(500L)
                    if (engine == null) {
                        startDetection()
                    }
                } else {
                    Log.e(TAG, "max restart attempts ($MAX_RESTART_ATTEMPTS) reached — giving up")
                }
            }
        }

        Log.d(TAG, "startDetection: engine started, detectionJob active")
    }

    private fun pauseDetection() {
        detectionJob?.cancel()
        detectionJob = null

        engine?.release()
        engine = null

        restartAttempts = 0

        Log.d(TAG, "pauseDetection: engine released")
    }

    private fun broadcastDetected() {
        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName)
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID_WAKE,
            Constants.NOTIFICATION_CHANNEL_NAME_WAKE,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "IRIS arka planda dinliyor"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID_WAKE)
            .setContentTitle(Constants.NOTIFICATION_TITLE_WAKE)
            .setContentText(Constants.NOTIFICATION_TEXT_WAKE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
