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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service for continuous wake word detection using openWakeWord.
 *
 * Library: xyz.rementia:openwakeword:0.1.5
 * Source verified: https://github.com/Re-MENTIA/openwakeword-android-kt
 *
 * Verified imports:
 *   WakeWordEngine   → com.rementia.openwakeword.lib.WakeWordEngine
 *   WakeWordModel    → com.rementia.openwakeword.lib.model.WakeWordModel
 *   DetectionMode    → com.rementia.openwakeword.lib.model.DetectionMode
 *
 * Verified API:
 *   WakeWordModel(name, modelPath, threshold)
 *   WakeWordEngine(context, models, detectionMode, detectionCooldownMs)
 *   engine.start()      — opens AudioRecord, begins detection
 *   engine.release()    — stops AudioRecord, frees resources
 *   engine.detections   — SharedFlow<WakeWordDetection>
 *   detection.model.name, detection.score
 *
 * Mic lifecycle:
 *   ACTION_PAUSE  — releases engine (frees AudioRecord) so voice pipeline can open its own
 *   ACTION_RESUME — recreates and starts engine after voice pipeline finishes
 *   This prevents dual-AudioRecord conflict (Android does not allow two concurrent AudioRecord instances)
 *
 * Required assets in app/src/main/assets/:
 *   - hey_jarvis.onnx
 *   - melspectrogram.onnx
 *   - embedding_model.onnx
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"

        const val ACTION_WAKE_WORD_DETECTED = "com.iris.assistant.WAKE_WORD_DETECTED"
        const val ACTION_START              = "com.iris.assistant.wake_word.START"
        const val ACTION_STOP               = "com.iris.assistant.wake_word.STOP"

        /**
         * Pause detection — releases AudioRecord so voice pipeline can use the mic.
         * Engine is fully released (not just paused) because WakeWordEngine has no pause API.
         */
        const val ACTION_PAUSE              = "com.iris.assistant.wake_word.PAUSE"

        /**
         * Resume detection — recreates engine after voice pipeline finishes.
         */
        const val ACTION_RESUME             = "com.iris.assistant.wake_word.RESUME"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var engine: WakeWordEngine? = null

    // Tracks whether we are intentionally paused (vs stopped)
    private var isPaused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID_WAKE, buildNotification())
        Log.d(TAG, "onCreate: foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> { isPaused = false; startDetection() }
            ACTION_PAUSE  -> { isPaused = true;  stopDetection()  }
            ACTION_RESUME -> { isPaused = false; startDetection() }
            ACTION_STOP   -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopDetection()
        serviceScope.cancel()
        Log.d(TAG, "onDestroy: service stopped")
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------
    // Detection
    // ---------------------------------------------------------------------------

    private fun startDetection() {
        if (engine != null) {
            Log.w(TAG, "startDetection: engine already running, ignoring")
            return
        }

        Log.d(TAG, "startDetection: initializing WakeWordEngine")

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
        )

        engine?.start()

        serviceScope.launch {
            try {
                engine?.detections?.collect { detection ->
                    Log.d(TAG, "Detected: ${detection.model.name} (score=${detection.score})")
                    broadcastDetected()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detection flow error", e)
                // Do not stopSelf() here — engine may have been released intentionally via PAUSE
            }
        }
    }

    private fun stopDetection() {
        engine?.release()
        engine = null
        Log.d(TAG, "stopDetection: engine released (isPaused=$isPaused)")
    }

    private fun broadcastDetected() {
        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName)
        })
    }

    // ---------------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------------

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