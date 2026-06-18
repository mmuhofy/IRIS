package com.iris.assistant.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.iris.assistant.service.voice.IrisVoiceInteractionService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition { !appViewModel.isReady.value }

        handleVoiceInteractionIntent(intent)
        requestAssistantRole()

        setContent {
            IrisApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceInteractionIntent(intent)
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT)
                    startActivity(intent)
                }
            } catch (_: Exception) {
                // Silently ignore — RoleManager not available on this device
            }
        }
    }

    private fun handleVoiceInteractionIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(IrisVoiceInteractionService.EXTRA_VOICE_INTERACTION, false) == true) {
            Log.d(TAG, "voice interaction triggered via intent")
            appViewModel.onVoiceInteractionTriggered()
        }
    }
}
