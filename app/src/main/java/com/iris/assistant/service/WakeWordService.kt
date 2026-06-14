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
import com.rementia.openwakeword.lib.WakeWordModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that runs openWakeWord continuously in the background.
 *
 * Uses WakeWordEngine (single model — 1 wake word, sequential processing).
 * On detection, broadcasts [ACTION_WAKE_WORD_DETECTED] so HomeViewModel can react.
 *
 * Required assets in app/src/main/assets/:
 *   - hey_jarvis.onnx        (prebuilt model from openWakeWord repo — MVP placeholder)
 *   - melspectrogram.onnx    (from openWakeWord repo)
 *   - embedding_model.onnx   (from openWakeWord repo)
 *
 * Download from:
 *   https://github.com/dscripka/openWakeWord/tree/main/openwakeword/resources/models
 *
 * UNTESTED — verify asset filenames and threshold against actual model before use.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"

        /** Broadcast action sent when wake word is detected. */
        const val ACTION_WAKE_WORD_DETECTED = "com.iris.assistant.WAKE_WORD_DETECTED"

        /** Start command — sent by HomeViewModel or boot receiver. */
        const val ACTION_START = "com.iris.assistant.wake_word.START"

        /** Stop command — sent when user disables background listening in Settings. */
        const val ACTION_STOP = "com.iris.assistant.wake_word.STOP"

        private const val NOTIFICATION_ID = 1001
    }

    // Service-scoped coroutine scope — cancelled in onDestroy()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // WakeWordEngine instance — created in startDetection(), released in stopDetection()
    private var engine: WakeWordEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "onCreate: foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDetection()
            ACTION_STOP  -> stopSelf()
        }
        // STICKY: if killed by system, restart with last intent
        return START_STICKY
    }

    override fun onDestroy() {
        stopDetection()
        serviceScope.cancel()
        Log.d(TAG, "onDestroy: service stopped")
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------
    // Wake word detection
    // ---------------------------------------------------------------------------

    /**
     * Initializes WakeWordEngine with hey_jarvis.onnx and starts listening.
     *
     * UNTESTED — verify:
     *   - Asset filenames match exactly what's placed in assets/
     *   - Threshold (0.5f) is appropriate for hey_jarvis model
     *   - WakeWordEngine constructor does not throw on these asset names
     */
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
            context            = applicationContext,
            models             = models,
            detectionCooldownMs = Constants.WAKE_WORD_COOLDOWN_MS
        )

        engine?.start()

        // Collect detections on service scope
        serviceScope.launch {
            engine?.detections?.collect { detection ->
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

    /**
     * Sends a local broadcast that HomeViewModel listens for.
     * HomeViewModel registers a BroadcastReceiver tied to the screen lifecycle.
     */
    private fun broadcastDetected() {
        val intent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName) // local only — not system-wide
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
            NotificationManager.IMPORTANCE_LOW // Low: no sound, shows in tray
        ).apply {
            description = "IRIS arka planda dinliyor"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID_WAKE)
            .setContentTitle(Constants.NOTIFICATION_TITLE_WAKE)
            .setContentText(Constants.NOTIFICATION_TEXT_WAKE)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a proper mic/iris icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)      // Cannot be dismissed by user while service runs
            .setSilent(true)       // No sound on post
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}