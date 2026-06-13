package com.iris.assistant.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import com.iris.assistant.ui.components.IrisButtonPrimary
import com.iris.assistant.ui.components.IrisButtonSecondary

// ---------------------------------------------------------------------------
// Shared skeleton
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingStepScreen(
    title      : String,
    description: String,
    buttonLabel: String,
    onNext     : () -> Unit,
    secondaryLabel: String? = null,
    onSecondary   : (() -> Unit)? = null
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text      = title,
            style     = MaterialTheme.typography.headlineMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = description,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        IrisButtonPrimary(text = buttonLabel, onClick = onNext)
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(12.dp))
            IrisButtonSecondary(text = secondaryLabel, onClick = onSecondary)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Microphone permission
// ---------------------------------------------------------------------------
@Composable
fun OnboardingMicScreen(onNext: () -> Unit) {
    var permissionDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onNext() else permissionDenied = true
    }

    OnboardingStepScreen(
        title       = "Mikrofon İzni",
        description = if (permissionDenied)
            "Mikrofon izni reddedildi. IRIS'in seni duyabilmesi için izin gerekli."
        else
            "IRIS'in seni duyabilmesi için mikrofon iznine ihtiyacımız var.",
        buttonLabel = "İzin Ver",
        onNext      = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        secondaryLabel = if (permissionDenied) "Atla" else null,
        onSecondary    = if (permissionDenied) onNext else null
    )
}

// ---------------------------------------------------------------------------
// Step 3 — Wake word test (placeholder — full impl Phase 2 wake word)
// ---------------------------------------------------------------------------
@Composable
fun OnboardingWakeWordScreen(onNext: () -> Unit) {
    OnboardingStepScreen(
        title       = "\"Hey IRIS\"",
        description = "Beni uyandırmak için \"Hey IRIS\" de. Wake word aktivasyonu Phase 2'de eklenecek.",
        buttonLabel = "Devam",
        onNext      = onNext
    )
}

// ---------------------------------------------------------------------------
// Step 4 — Quick demo
// ---------------------------------------------------------------------------
@Composable
fun OnboardingDemoScreen(onNext: () -> Unit) {
    OnboardingStepScreen(
        title       = "Hazır!",
        description = "Ana ekranda 🎤 butonuna bas ve bir şey sor. Örneğin: \"Bugün hava nasıl?\"",
        buttonLabel = "Devam",
        onNext      = onNext
    )
}

// ---------------------------------------------------------------------------
// Step 5 — Battery optimization + onboarding complete
// ---------------------------------------------------------------------------
@Composable
fun OnboardingBatteryScreen(
    onFinish : () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val isIgnoringBatteryOptimizations = remember {
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    OnboardingStepScreen(
        title       = "Arka Plan İzni",
        description = if (isIgnoringBatteryOptimizations)
            "Pil optimizasyonu zaten devre dışı. Hazırsın!"
        else
            "IRIS'in arka planda çalışabilmesi için pil optimizasyonunu devre dışı bırak.",
        buttonLabel = if (isIgnoringBatteryOptimizations) "Başla!" else "Ayarlara Git",
        onNext = {
            if (!isIgnoringBatteryOptimizations) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
            viewModel.onOnboardingCompleted()
            onFinish()
        },
        secondaryLabel = if (!isIgnoringBatteryOptimizations) "Atla" else null,
        onSecondary    = if (!isIgnoringBatteryOptimizations) {
            {
                viewModel.onOnboardingCompleted()
                onFinish()
            }
        } else null
    )
}