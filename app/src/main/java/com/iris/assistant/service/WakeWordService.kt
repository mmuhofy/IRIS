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
import com.rementia.openwakeword.lib.DetectionMode
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.WakeWordModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs openWakeWord continuously in the background.
 *
 * Library: xyz.rementia:openwakeword:0.1.5
 * API verified against: https://github.com/Re-MENTIA/openwakeword-android-kt (README, v0.1.5)
 *
 * On detection, broadcasts [ACTION_WAKE_WORD_DETECTED] so HomeViewModel can react.
 *
 * Required assets in app/src/main/assets/:
 *   - hey_jarvis.onnx        (prebuilt model — download from openWakeWord repo)
 *   - melspectrogram.onnx    (from openWakeWord repo)
 *   - embedding_model.onnx   (from openWakeWord repo)
 *
 * Download from:
 *   https://github.com/dscripka/openWakeWord/tree/main/openwakeword/resources/models
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"

        /** Broadcast action sent when wake word is detected. */
        const val ACTION_WAKE_WORD_DETECTED = "com.iris.assistant.WAKE_WORD_DETECTED"

        const val ACTION_START = "com.iris.assistant.wake_word.START"
        const val ACTION_STOP  = "com.iris.assistant.wake_word.STOP"
    }

    // Service-scoped coroutine scope — cancelled in onDestroy()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var engine: WakeWordEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID_WAKE, buildNotification())
        Log.d(TAG, "onCreate: foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDetection()
            ACTION_STOP  -> stopSelf()
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

        // WakeWordModel(name, modelPath, threshold)
        // Verified constructor: data class WakeWordModel(val name: String, val modelPath: String, val threshold: Float = 0.5f)
        val models = listOf(
            WakeWordModel(
                name      = Constants.WAKE_WORD_MODEL_NAME,
                modelPath = Constants.WAKE_WORD_MODEL_FILE,
                threshold = Constants.WAKE_WORD_THRESHOLD
            )
        )

        // WakeWordEngine: standard engine — correct for single model use case
        // Verified constructor: WakeWordEngine(context, models, detectionMode, detectionCooldownMs, scope)
        engine = WakeWordEngine(
            context             = applicationContext,
            models              = models,
            detectionMode       = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = Constants.WAKE_WORD_COOLDOWN_MS
        )

        engine?.start()

        serviceScope.launch {
            engine?.detections
                ?.catch { e -> Log.e(TAG, "Detection flow error", e) }
                ?.collect { detection ->
                    Log.d(TAG, "Wake word detected: ${detection.model.name} (score=${detection.score})")
                    broadcastDetected()
                }
        }
    }

    private fun stopDetection() {
        engine?.release()
        engine = null
        Log.d(TAG, "stopDetection: engine released")
    }

    private fun broadcastDetected() {
        val intent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName) // local only
        }
        sendBroadcast(intent)
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