package com.iris.assistant.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.iris.assistant.ui.components.IrisButtonPrimary
import com.iris.assistant.ui.components.IrisButtonSecondary
import com.iris.assistant.ui.theme.IrisTheme

@Composable
private fun OnboardingStepLayout(
    step: Int,
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    onNext: () -> Unit,
    buttonEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(currentStep = step, totalSteps = 5)

        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = IrisTheme.colors.primary
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        content()

        Spacer(Modifier.weight(1f))

        IrisButtonPrimary(
            text = buttonLabel,
            onClick = onNext,
            enabled = buttonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (secondaryLabel != null) 0.dp else 48.dp)
        )

        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(12.dp))
            IrisButtonSecondary(
                text = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            )
        }
    }
}

@Composable
fun OnboardingMicScreen(onNext: () -> Unit) {
    var permissionDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onNext() else permissionDenied = true
    }

    OnboardingStepLayout(
        step = 2,
        icon = Icons.Filled.Mic,
        title = "Mikrofon İzni",
        description = if (permissionDenied)
            "Mikrofon izni reddedildi.\nIRIS'in seni duyabilmesi için izin gerekli."
        else
            "IRIS'in seni duyabilmesi için\nmikrofon iznine ihtiyacımız var.",
        buttonLabel = "İzin Ver",
        onNext = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        secondaryLabel = if (permissionDenied) "Atla" else null,
        onSecondary = if (permissionDenied) onNext else null
    )
}

@Composable
fun OnboardingWakeWordScreen(onNext: () -> Unit) {
    OnboardingStepLayout(
        step = 3,
        icon = Icons.Filled.VolumeUp,
        title = "\"Hey IRIS\"",
        description = "Beni uyandırmak için \"Hey IRIS\" de.\nWake word desteği yakında eklenecek.",
        buttonLabel = "Devam",
        onNext = onNext
    )
}

@Composable
fun OnboardingDemoScreen(onNext: () -> Unit) {
    OnboardingStepLayout(
        step = 4,
        icon = Icons.Filled.PlayArrow,
        title = "Hazır!",
        description = "Ana ekranda 🎤 butonuna bas ve bir şey sor.\nÖrneğin: \"Bugün hava nasıl?\"",
        buttonLabel = "Devam",
        onNext = onNext
    )
}

@Composable
fun OnboardingBatteryScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel
) {
    val context = LocalContext.current

    val isIgnoringBatteryOptimizations = remember {
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    OnboardingStepLayout(
        step = 5,
        icon = Icons.Filled.BatteryChargingFull,
        title = "Arka Plan İzni",
        description = if (isIgnoringBatteryOptimizations)
            "Pil optimizasyonu zaten devre dışı.\nHazırsın!"
        else
            "IRIS'in arka planda çalışabilmesi için\npil optimizasyonunu devre dışı bırak.",
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
        onSecondary = if (!isIgnoringBatteryOptimizations) {
            {
                viewModel.onOnboardingCompleted()
                onFinish()
            }
        } else null
    )
}
