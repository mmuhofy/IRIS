package com.iris.assistant.ui.onboarding

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import com.phosphor.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iris.assistant.service.WakeWordService
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
    onBack: (() -> Unit)? = null,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = PhIcons.Regular.ArrowLeft,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(1f))
            StepIndicator(currentStep = step, totalSteps = 6)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

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
fun OnboardingMicScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var permissionDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onNext() else permissionDenied = true
    }

    OnboardingStepLayout(
        step = 2,
        icon = PhIcons.Filled.MicrophoneFill,
        title = "Mikrofon İzni",
        description = if (permissionDenied)
            "Mikrofon izni reddedildi.\nIRIS'in seni duyabilmesi için izin gerekli."
        else
            "IRIS'in seni duyabilmesi için\nmikrofon iznine ihtiyacımız var.",
        buttonLabel = "İzin Ver",
        onNext = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        onBack = onBack,
        secondaryLabel = if (permissionDenied) "Atla" else null,
        onSecondary = if (permissionDenied) onNext else null
    )
}

private enum class WakeWordTestState { IDLE, LISTENING, DETECTED }

@Composable
fun OnboardingWakeWordScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var testState by remember { mutableStateOf(WakeWordTestState.IDLE) }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                    testState = WakeWordTestState.DETECTED
                }
            }
        }
    }

    val currentOnNext by rememberUpdatedState(onNext)

    DisposableEffect(Unit) {
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }

    val buttonLabel = when (testState) {
        WakeWordTestState.IDLE      -> "Test Et"
        WakeWordTestState.LISTENING -> "Dinleniyor..."
        WakeWordTestState.DETECTED  -> "\u2705 Algılandı!"
    }

    OnboardingStepLayout(
        step = 3,
        icon = PhIcons.Filled.SpeakerHighFill,
        title = "\"Hey IRIS\"",
        description = when (testState) {
            WakeWordTestState.IDLE      -> "Beni uyandırmak için \"Hey IRIS\" de.\nAşağıdaki butona bas ve \"Hey IRIS\" söyle."
            WakeWordTestState.LISTENING -> "\uD83D\uDC42 \"Hey IRIS\" diyerek beni uyandırmayı dene..."
            WakeWordTestState.DETECTED  -> "Harika! Seni duydum \uD83D\uDC4D"
        },
        buttonLabel = buttonLabel,
        buttonEnabled = testState != WakeWordTestState.LISTENING,
        onNext = {
            when (testState) {
                WakeWordTestState.IDLE -> {
                    testState = WakeWordTestState.LISTENING
                    @Suppress("DEPRECATION")
                    context.startService(Intent(context, WakeWordService::class.java).apply {
                        action = WakeWordService.ACTION_START
                    })
                }
                WakeWordTestState.LISTENING -> { /* disabled, no-op */ }
                WakeWordTestState.DETECTED -> {
                    context.stopService(Intent(context, WakeWordService::class.java))
                    currentOnNext()
                }
            }
        },
        onBack = onBack,
        secondaryLabel = if (testState == WakeWordTestState.LISTENING) "Atla" else null,
        onSecondary = if (testState == WakeWordTestState.LISTENING) {
            {
                context.stopService(Intent(context, WakeWordService::class.java))
                testState = WakeWordTestState.IDLE
                currentOnNext()
            }
        } else null
    ) {
        Spacer(Modifier.height(12.dp))
        WakeWordListeningIndicator(state = testState)
    }
}

@Composable
private fun WakeWordListeningIndicator(state: WakeWordTestState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val alpha = if (state == WakeWordTestState.LISTENING) pulseAlpha else 1f

    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (state) {
                WakeWordTestState.DETECTED -> PhIcons.Filled.CheckCircleFill
                else -> PhIcons.Regular.Microphone
            },
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = when (state) {
                WakeWordTestState.DETECTED -> IrisTheme.colors.primary
                WakeWordTestState.LISTENING -> IrisTheme.colors.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun OnboardingDemoScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    OnboardingStepLayout(
        step = 4,
        icon = PhIcons.Filled.PlayFill,
        title = "Hazır!",
        description = "Ana ekranda \uD83C\uDFA4 butonuna bas ve bir şey sor.\nÖrneğin: \"Bugün hava nasıl?\"",
        buttonLabel = "Devam",
        onNext = onNext,
        onBack = onBack
    )
}

@Composable
fun OnboardingAssistantScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    OnboardingStepLayout(
        step = 5,
        icon = PhIcons.Regular.MagicWand,
        title = "Varsayılan Asistan",
        description = "IRIS'i varsayılan asistan yapmak için\nAyarlar > Uygulamalar > Varsayılan Uygulamalar\n> Dijital Asistan Uygulaması yolunu izle\nve IRIS'i seç.",
        buttonLabel = "Ayarlara Git",
        onNext = {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        },
        onBack = onBack,
        secondaryLabel = "Atla",
        onSecondary = onNext
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Böylece güç düğmesine uzun basarak\nIRIS'i açabilirsin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingBatteryScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel
) {
    val context = LocalContext.current

    val isIgnoringBatteryOptimizations = remember {
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    OnboardingStepLayout(
        step = 5,
        icon = PhIcons.Filled.BatteryChargingFill,
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
        onBack = onBack,
        secondaryLabel = if (!isIgnoringBatteryOptimizations) "Atla" else null,
        onSecondary = if (!isIgnoringBatteryOptimizations) {
            {
                viewModel.onOnboardingCompleted()
                onFinish()
            }
        } else null
    )
}
