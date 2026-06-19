package com.iris.assistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.iris.assistant.data.tools.screen.ScreenInteractionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class IrisAccessibilityService : AccessibilityService() {

    @Inject lateinit var screenRepository: ScreenInteractionRepository

    private val bgScope = CoroutineScope(Dispatchers.Default)
    private var refreshJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        bgScope.cancel()
        instance = null
        screenRepository.clear()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                debouncedRefresh()
            }
        }
    }

    override fun onInterrupt() {}

    private fun debouncedRefresh() {
        refreshJob?.cancel()
        refreshJob = bgScope.launch {
            delay(150)
            withContext(Dispatchers.Main) {
                val root = rootInActiveWindow ?: return@withContext
                screenRepository.updateRootNode(root)
            }
        }
    }

    fun performGlobalActionCompat(action: Int): Boolean {
        return performGlobalAction(action)
    }

    companion object {
        var instance: IrisAccessibilityService? = null
            private set
    }
}
