package com.iris.assistant.service.wakeword

import android.content.Context
import android.util.Log
import com.iris.assistant.util.Constants
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeWordManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WakeWordManager"
        private const val MAX_RESTART_ATTEMPTS = 3
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var engine: WakeWordEngine? = null
    private var detectionJob: Job? = null
    @Volatile private var isShuttingDown = false

    private val _detections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val detections: SharedFlow<Unit> = _detections.asSharedFlow()

    fun startListening() {
        if (engine != null && detectionJob?.isActive == true) {
            Log.w(TAG, "startListening: already running")
            return
        }
        stopListening()
        isShuttingDown = false

        val models = Constants.WAKE_WORD_MODELS.map { entry ->
            WakeWordModel(
                name      = entry.name,
                modelPath = entry.file,
                threshold = entry.threshold,
            )
        }

        engine = WakeWordEngine(
            context             = context,
            models              = models,
            detectionMode       = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = Constants.WAKE_WORD_COOLDOWN_MS
        ).also { it.start() }

        val currentEngine = engine ?: return
        var restartAttempts = 0

        detectionJob = scope.launch {
            try {
                currentEngine.detections.collect { detection ->
                    Log.d(TAG, "Detected: ${detection.model.name} score=${detection.score}")
                    _detections.tryEmit(Unit)
                }
            } catch (e: Exception) {
                Log.w(TAG, "detection failed: ${e.message}")
                if (isShuttingDown) return@launch
                currentEngine.release()
                engine = null
                restartAttempts++
                if (restartAttempts <= MAX_RESTART_ATTEMPTS) {
                    delay(500L)
                    if (!isShuttingDown) startListening()
                } else {
                    Log.e(TAG, "max restart attempts reached")
                }
            }
        }
    }

    fun stopListening() {
        isShuttingDown = true
        detectionJob?.cancel()
        detectionJob = null
        try { Thread.sleep(100) } catch (_: InterruptedException) {}
        engine?.release()
        engine = null
        isShuttingDown = false
    }
}
