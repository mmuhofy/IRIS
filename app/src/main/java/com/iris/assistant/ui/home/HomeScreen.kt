package com.iris.assistant.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenChat    : () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // TODO: Top bar (menu | settings | chat-mode toggle) — Phase 1
            // TODO: Iris Core animation (center) — Phase 1
            // TODO: Status text — Phase 1

            // Bottom quick controls placeholder
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // TODO: Replace with IrisButton — mic toggle
                Text("🎤", style = MaterialTheme.typography.headlineMedium)
                // TODO: Replace with IrisButton — stop/interrupt
                Text("⏹", style = MaterialTheme.typography.headlineMedium)
                // TODO: Replace with IrisButton — screen control toggle
                Text("📺", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}