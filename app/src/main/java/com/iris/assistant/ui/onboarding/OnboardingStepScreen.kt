package com.iris.assistant.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme

// ---------------------------------------------------------------------------
// Shared skeleton composable — reused by all onboarding steps
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingStepScreen(
    title      : String,
    description: String,
    buttonLabel: String,
    onNext     : () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        // TODO: Replace with IrisButton (Phase 1 components)
        Button(
            onClick = onNext,
            colors  = ButtonDefaults.buttonColors(containerColor = IrisTheme.colors.primary)
        ) {
            Text(buttonLabel)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Microphone permission
// ---------------------------------------------------------------------------
@Composable
fun OnboardingMicScreen(onNext: () -> Unit) {
    OnboardingStepScreen(
        title       = "Mikrofon İzni",
        description = "IRIS'in seni duyabilmesi için mikrofon iznine ihtiyacımız var.",
        buttonLabel = "İzin Ver",
        onNext      = onNext
        // TODO: Actual permission request via rememberLauncherForActivityResult
    )
}

// ---------------------------------------------------------------------------
// Step 3 — Wake word test
// ---------------------------------------------------------------------------
@Composable
fun OnboardingWakeWordScreen(onNext: () -> Unit) {
    OnboardingStepScreen(
        title       = "\"Hey IRIS\"",
        description = "Beni uyandırmak için \"Hey IRIS\" de. Hazır olduğunda devam et.",
        buttonLabel = "Devam",
        onNext      = onNext
        // TODO: Wake word detection confirmation (Phase 1 voice pipeline)
    )
}

// ---------------------------------------------------------------------------
// Step 4 — Quick demo
// ---------------------------------------------------------------------------
@Composable
fun OnboardingDemoScreen(onNext: () -> Unit) {
    OnboardingStepScreen(
        title       = "Hızlı Deneme",
        description = "\"Saat kaç?\" diyerek beni dene.",
        buttonLabel = "Devam",
        onNext      = onNext
        // TODO: Wire to voice pipeline (Phase 1)
    )
}

// ---------------------------------------------------------------------------
// Step 5 — Battery optimization
// ---------------------------------------------------------------------------
@Composable
fun OnboardingBatteryScreen(onFinish: () -> Unit) {
    OnboardingStepScreen(
        title       = "Arka Plan İzni",
        description = "IRIS'in arka planda çalışabilmesi için pil optimizasyonunu devre dışı bırak.",
        buttonLabel = "Ayarlara Git",
        onNext      = onFinish
        // TODO: Deep link to battery optimization settings (Phase 1)
    )
}