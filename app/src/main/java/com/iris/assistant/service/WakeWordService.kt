package com.iris.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
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

/**
 * Simplified wake word service — used ONLY by OnboardingWakeWordScreen for the
 * "Hey IRIS" test step. No foreground notification, no background persistence.
 *
 * The main app uses WakeWordManager (singleton) instead of this service.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val MAX_RESTART_ATTEMPTS = 3

        const val ACTION_WAKE_WORD_DETECTED = "com.iris.assistant.WAKE_WORD_DETECTED"
        const val ACTION_START = "com.iris.assistant.wake_word.START"
        const val ACTION_STOP  = "com.iris.assistant.wake_word.STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var detectionJob: Job? = null
    private var engine: WakeWordEngine? = null
    private var restartAttempts = 0
    private var isShuttingDown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                if (!Constants.WAKE_WORD_ENABLED) {
                    Log.w(TAG, "wake word disabled via Constants.WAKE_WORD_ENABLED")
                    return START_NOT_STICKY
                }
                startDetection()
            }
            ACTION_STOP  -> stopSelf()
            else         -> Log.w(TAG, "unknown action")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopDetection()
        serviceScope.cancel()
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun startDetection() {
        if (engine != null) {
            Log.w(TAG, "startDetection: already running")
            return
        }
        isShuttingDown = false
        restartAttempts = 0

        val models = Constants.WAKE_WORD_MODELS.map { entry ->
            WakeWordModel(
                name      = entry.name,
                modelPath = entry.file,
                threshold = entry.threshold,
            )
        }

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
                if (isShuttingDown) return@launch
                currentEngine.release()
                engine = null
                restartAttempts++
                if (restartAttempts <= MAX_RESTART_ATTEMPTS) {
                    Log.d(TAG, "restarting (attempt $restartAttempts/$MAX_RESTART_ATTEMPTS)")
                    delay(500L)
                    if (!isShuttingDown && engine == null) startDetection()
                } else {
                    Log.e(TAG, "max restart attempts reached")
                }
            }
        }
    }

    private fun stopDetection() {
        isShuttingDown = true
        detectionJob?.cancel()
        detectionJob = null
        try { Thread.sleep(100) } catch (_: InterruptedException) {}
        engine?.release()
        engine = null
        restartAttempts = 0
    }

    private fun broadcastDetected() {
        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).apply { setPackage(packageName) })
    }
}
