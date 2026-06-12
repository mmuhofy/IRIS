package com.iris.assistant.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenChat    : () -> Unit,
    onOpenSettings: () -> Unit
) {
    // TODO: Drive these from HomeViewModel (Phase 1 voice pipeline)
    var coreState   by remember { mutableStateOf(IrisCoreState.IDLE) }
    var amplitude   by remember { mutableFloatStateOf(0f) }
    var ttsProgress by remember { mutableFloatStateOf(0f) }

    val statusText = when (coreState) {
        IrisCoreState.IDLE      -> "Dinlemeye hazır"
        IrisCoreState.LISTENING -> "Dinliyorum..."
        IrisCoreState.THINKING  -> "Düşünüyorum..."
        IrisCoreState.SPEAKING  -> "Konuşuyorum..."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Top bar ---
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("☰", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("IRIS", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("💬",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // TODO: onClick → onOpenChat (Phase 1)
                    )
                    Text("⚙",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // TODO: onClick → onOpenSettings (Phase 1)
                    )
                }
            }

            // --- Center: Iris Core + status text ---
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IrisCoreAnimation(
                    state       = coreState,
                    amplitude   = amplitude,
                    ttsProgress = ttsProgress
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text      = statusText,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // --- Bottom quick controls ---
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // TODO: Replace emoji placeholders with Phosphor icon IrisButton variants (Phase 1)
                Text("🎤", style = MaterialTheme.typography.headlineMedium)
                Text("⏹",  style = MaterialTheme.typography.headlineMedium)
                Text("📺", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}