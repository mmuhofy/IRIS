package com.iris.assistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.iris.assistant.data.tools.screen.ScreenInteractionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IrisAccessibilityService : AccessibilityService() {

    @Inject lateinit var screenRepository: ScreenInteractionRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        screenRepository.clear()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                refreshScreenDump()
            }
        }
    }

    override fun onInterrupt() {}

    private fun refreshScreenDump() {
        val root = rootInActiveWindow ?: return
        screenRepository.updateRootNode(root)
    }

    fun performGlobalActionCompat(action: Int): Boolean {
        return performGlobalAction(action)
    }

    companion object {
        var instance: IrisAccessibilityService? = null
            private set
    }
}
