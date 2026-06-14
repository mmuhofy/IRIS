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
 *   engine.start()    — opens AudioRecord, begins detection
 *   engine.release()  — stops AudioRecord, frees resources
 *   engine.detections — SharedFlow<WakeWordDetection>
 *   detection.model.name, detection.score
 *
 * Mic lifecycle — prevents dual AudioRecord conflict:
 *   ACTION_PAUSE  → cancels detectionJob + releases engine (frees AudioRecord)
 *   ACTION_RESUME → recreates engine + starts new detectionJob
 *
 * Bug fixes vs previous version:
 *   1. detectionJob tracked separately — cancelled on PAUSE, prevents stale collect loops
 *   2. START_NOT_STICKY used — prevents zombie restart after PAUSE-induced stop
 *      (START_STICKY would restart with null intent → no-op → engine never restarts)
 *   3. engine null-check in startDetection() guards against double-start
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"

        const val ACTION_WAKE_WORD_DETECTED = "com.iris.assistant.WAKE_WORD_DETECTED"
        const val ACTION_START              = "com.iris.assistant.wake_word.START"
        const val ACTION_STOP               = "com.iris.assistant.wake_word.STOP"

        /**
         * Pause detection — cancels collection job and releases AudioRecord.
         * Voice pipeline calls this before opening its own AudioRecorder.
         */
        const val ACTION_PAUSE              = "com.iris.assistant.wake_word.PAUSE"

        /**
         * Resume detection — recreates engine and starts fresh collection job.
         * Voice pipeline calls this after TTS completes and AudioRecorder is released.
         */
        const val ACTION_RESUME             = "com.iris.assistant.wake_word.RESUME"
    }

    // Service-lifetime scope — cancelled only in onDestroy()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Detection collection job — cancelled on PAUSE, replaced on RESUME
    // Tracked separately so cancel() only stops collection, not the whole serviceScope
    private var detectionJob: Job? = null

    private var engine: WakeWordEngine? = null

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
            ACTION_RESUME -> startDetection() // same as START — engine always fully recreated
            ACTION_STOP   -> stopSelf()
            else          -> Log.w(TAG, "onStartCommand: unknown or null action — ignoring")
        }
        // NOT_STICKY: if killed by system while paused, do not auto-restart.
        // HomeViewModel will restart explicitly when needed via ACTION_START/ACTION_RESUME.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pauseDetection() // cancel job + release engine
        serviceScope.cancel()
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------
    // Detection lifecycle
    // ---------------------------------------------------------------------------

    private fun startDetection() {
        // Guard: do not create a second engine if already running
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
        )

        engine?.start()

        // Launch a new detection job — previous job (if any) was already cancelled in pauseDetection()
        detectionJob = serviceScope.launch {
            try {
                engine?.detections?.collect { detection ->
                    Log.d(TAG, "Detected: ${detection.model.name} score=${detection.score}")
                    broadcastDetected()
                }
            } catch (e: Exception) {
                Log.w(TAG, "detectionJob: ended (${e.message}) — restarting in 500ms")
                engine = null
                kotlinx.coroutines.delay(500L)
                if (engine == null) {
                    startDetection()
                }
            }
        }

        Log.d(TAG, "startDetection: engine started, detectionJob active")
    }

    /**
     * Cancels the detection job and releases the engine.
     * After this call, AudioRecord is fully released and available to AudioRecorder.
     * Safe to call multiple times (idempotent).
     */
    private fun pauseDetection() {
        detectionJob?.cancel()
        detectionJob = null

        engine?.release()
        engine = null

        Log.d(TAG, "pauseDetection: detectionJob cancelled, engine released")
    }

    private fun broadcastDetected() {
        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).apply {
            setPackage(packageName) // local only — package-scoped
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