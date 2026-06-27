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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.iris.assistant.service.WakeWordService
import com.iris.assistant.ui.components.IrisButtonPrimary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.ArrowRightFill
import com.phosphor.icons.filled.BatteryChargingFill
import com.phosphor.icons.filled.CheckCircleFill
import com.phosphor.icons.filled.MicrophoneFill
import com.phosphor.icons.filled.PlayFill
import com.phosphor.icons.filled.SpeakerHighFill
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.Lightning
import com.phosphor.icons.regular.MagicWand
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.ShieldCheck

// ═══════════════════════════════════════════════════════════════════════════════
// Shared step layout
// ═══════════════════════════════════════════════════════════════════════════════

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
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    // Entrance animation — triggers once per composition (i.e. once per screen show)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val contentAlpha by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label         = "stepAlpha"
    )
    val contentOffsetY by animateDpAsState(
        targetValue   = if (appeared) 0.dp else 22.dp,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label         = "stepOffsetY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IrisTheme.colors.primary.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY   = 480f,
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress bar (always visible, not animated in)
            SegmentedStepIndicator(currentStep = step, totalSteps = 6)

            Spacer(Modifier.height(8.dp))

            // Back button row — left-aligned
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Animated content block
            Column(
                modifier = Modifier
                    .offset(y = contentOffsetY)
                    .alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingIconContainer(
                    icon          = icon,
                    containerSize = 110.dp,
                    iconSize      = 44.dp
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text       = title,
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text      = description,
                    style     = MaterialTheme.typography.bodyLarge,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Extra per-screen content (e.g. privacy rows, waveform, chips)
                content()
            }

            Spacer(Modifier.weight(1f))

            // CTA button + optional text-style skip link
            Column(
                modifier = Modifier
                    .alpha(contentAlpha)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IrisButtonPrimary(
                    text     = buttonLabel,
                    onClick  = onNext,
                    enabled  = buttonEnabled,
                    modifier = Modifier.fillMaxWidth()
                )

                if (skipLabel != null && onSkip != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onSkip) {
                        Text(
                            text  = skipLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared sub-composables
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Small info row with an icon pill + label text.
 * Used for privacy/benefit callouts on mic and battery screens.
 * UNTESTED — verify PhIcons.Regular.ShieldCheck exists in the Phosphor version used
 */
@Composable
private fun StepInfoRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
                tint               = IrisTheme.colors.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Small chip showing an example voice command.
 * Used on the Demo screen.
 */
@Composable
private fun ExampleCommandChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                width = 1.dp,
                color = IrisTheme.colors.primary.copy(alpha = 0.30f),
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text  = "\"$text\"",
            style = MaterialTheme.typography.bodySmall,
            color = IrisTheme.colors.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Numbered instruction row — used on the Assistant (default assistant) screen.
 */
@Composable
private fun NumberedStepRow(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text  = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = IrisTheme.colors.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Step 2 — Microphone
// ═══════════════════════════════════════════════════════════════════════════════

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
        step        = 2,
        icon        = PhIcons.Filled.MicrophoneFill,
        title       = "Mikrofon İzni",
        description = if (permissionDenied)
            "İzin reddedildi. IRIS'in seni duyabilmesi için mikrofon izni gereklidir."
        else
            "IRIS'in seni duyabilmesi için mikrofon iznine ihtiyacımız var.",
        buttonLabel = if (permissionDenied) "Tekrar Dene" else "İzin Ver",
        onNext      = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        onBack      = onBack,
        skipLabel   = if (permissionDenied) "Atla" else null,
        onSkip      = if (permissionDenied) onNext else null
    ) {
        Spacer(Modifier.height(24.dp))
        // UNTESTED — verify PhIcons.Regular.ShieldCheck exists
        StepInfoRow(
            icon = PhIcons.Regular.ShieldCheck,
            text = "Ses kaydı yapılmaz — anlık olarak işlenir ve silinir"
        )
        Spacer(Modifier.height(8.dp))
        StepInfoRow(
            icon = PhIcons.Regular.Microphone,
            text = "Arka planda dinleme yalnızca açık bırakırsan çalışır"
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Step 3 — Wake Word
// ═══════════════════════════════════════════════════════════════════════════════

private enum class WakeWordTestState { IDLE, LISTENING, DETECTED }

@Composable
fun OnboardingWakeWordScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context    = LocalContext.current
    var testState  by remember { mutableStateOf(WakeWordTestState.IDLE) }

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
        WakeWordTestState.DETECTED  -> "Harika, Devam Et"
    }

    val description = when (testState) {
        WakeWordTestState.IDLE      -> "Beni uyandırmak için \"Hey IRIS\" diyebilirsin.\nTest etmek için aşağıdaki butona bas."
        WakeWordTestState.LISTENING -> "Seni dinliyorum... \"Hey IRIS\" de."
        WakeWordTestState.DETECTED  -> "Seni duydum! Her yerden uyandırabilirsin."
    }

    OnboardingStepLayout(
        step           = 3,
        icon           = PhIcons.Filled.SpeakerHighFill,
        title          = "\"Hey IRIS\"",
        description    = description,
        buttonLabel    = buttonLabel,
        buttonEnabled  = testState != WakeWordTestState.LISTENING,
        onNext         = {
            when (testState) {
                WakeWordTestState.IDLE -> {
                    testState = WakeWordTestState.LISTENING
                    @Suppress("DEPRECATION")
                    context.startService(
                        Intent(context, WakeWordService::class.java).apply {
                            action = WakeWordService.ACTION_START
                        }
                    )
                }
                WakeWordTestState.LISTENING -> { /* disabled — no-op */ }
                WakeWordTestState.DETECTED  -> {
                    context.stopService(Intent(context, WakeWordService::class.java))
                    currentOnNext()
                }
            }
        },
        onBack    = onBack,
        skipLabel = if (testState == WakeWordTestState.LISTENING) "Atla" else null,
        onSkip    = if (testState == WakeWordTestState.LISTENING) {
            {
                context.stopService(Intent(context, WakeWordService::class.java))
                testState = WakeWordTestState.IDLE
                currentOnNext()
            }
        } else null
    ) {
        Spacer(Modifier.height(28.dp))
        WakeWordVisualizer(state = testState)
    }
}

/**
 * Animated visualizer for the wake-word test.
 *
 * IDLE     → static mic icon, muted color
 * LISTENING → two pulsing ripple rings + mic icon, primary color
 * DETECTED  → checkmark icon, primary color, no rings
 *
 * Ring stagger uses StartOffset — if the Compose version does not have
 * StartOffset in androidx.compose.animation.core, replace the ring2 spec
 * with tween(durationMillis = 1400, delayMillis = 700) as a fallback.
 * UNTESTED — verify StartOffset import compiles with the project's Compose BOM.
 */
@Composable
private fun WakeWordVisualizer(state: WakeWordTestState) {
    val primary = IrisTheme.colors.primary

    val infiniteTransition = rememberInfiniteTransition(label = "wakeWordRipple")

    // Ring 1 — starts immediately
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation            = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode           = RepeatMode.Restart,
            initialStartOffset   = StartOffset(0)   // UNTESTED — see kdoc above
        ),
        label = "ring1Scale"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation          = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode         = RepeatMode.Restart,
            initialStartOffset = StartOffset(0)
        ),
        label = "ring1Alpha"
    )

    // Ring 2 — starts 700 ms offset for stagger effect
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation          = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode         = RepeatMode.Restart,
            initialStartOffset = StartOffset(700)   // UNTESTED — see kdoc above
        ),
        label = "ring2Scale"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation          = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode         = RepeatMode.Restart,
            initialStartOffset = StartOffset(700)
        ),
        label = "ring2Alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.size(88.dp)
    ) {
        if (state == WakeWordTestState.LISTENING) {
            // Ripple ring 2 (outer — more transparent)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(ring2Scale)
                    .alpha(ring2Alpha * 0.6f)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.25f))
            )
            // Ripple ring 1 (inner — more opaque)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(ring1Scale)
                    .alpha(ring1Alpha)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.30f))
            )
        }

        // Center icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        WakeWordTestState.DETECTED  -> primary.copy(alpha = 0.15f)
                        WakeWordTestState.LISTENING -> primary.copy(alpha = 0.12f)
                        WakeWordTestState.IDLE      -> MaterialTheme.colorScheme.surface
                    }
                )
        ) {
            Icon(
                imageVector = when (state) {
                    WakeWordTestState.DETECTED -> PhIcons.Filled.CheckCircleFill
                    else                       -> PhIcons.Regular.Microphone
                },
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = when (state) {
                    WakeWordTestState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    else                  -> primary
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Step 4 — Demo
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingDemoScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    OnboardingStepLayout(
        step        = 4,
        icon        = PhIcons.Filled.PlayFill,
        title       = "Dene Bakalım",
        description = "Ana ekranda mikrofon butonuna basıp bir şey sor.",
        buttonLabel = "Devam",
        onNext      = onNext,
        onBack      = onBack
    ) {
        Spacer(Modifier.height(24.dp))

        // Example command chips
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            ExampleCommandChip(text = "Bugün hava nasıl?")
            ExampleCommandChip(text = "Alarm kur")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            ExampleCommandChip(text = "Not al")
            ExampleCommandChip(text = "Mesaj gönder")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Step 5 — Default Assistant
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingAssistantScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    OnboardingStepLayout(
        step        = 5,
        icon        = PhIcons.Regular.MagicWand,
        title       = "Varsayılan Asistan",
        description = "IRIS'i varsayılan asistan yaparsan güç tuşuyla açabilirsin.",
        buttonLabel = "Ayarlara Git",
        onNext      = {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        },
        onBack    = onBack,
        skipLabel = "Atla",
        onSkip    = onNext
    ) {
        Spacer(Modifier.height(20.dp))

        Column(
            modifier            = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NumberedStepRow(1, "Ayarlar > Uygulamalar > Varsayılan Uygulamalar")
            NumberedStepRow(2, "Dijital Asistan Uygulaması seçeneğini aç")
            NumberedStepRow(3, "Listeden IRIS'i seç")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Step 6 — Battery Optimization
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingBatteryScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel
) {
    val context = LocalContext.current

    val isIgnoring = remember {
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    OnboardingStepLayout(
        step        = 6,   // FIX: was incorrectly 5 in the original
        icon        = PhIcons.Filled.BatteryChargingFill,
        title       = "Arka Plan İzni",
        description = if (isIgnoring)
            "Pil optimizasyonu zaten devre dışı. Hazırsın!"
        else
            "IRIS'in arka planda uyanık kalabilmesi için pil optimizasyonunu devre dışı bırak.",
        buttonLabel = if (isIgnoring) "Hadi Başlayalım!" else "Ayarlara Git",
        onNext      = {
            if (!isIgnoring) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply { data = Uri.parse("package:${context.packageName}") }
                context.startActivity(intent)
            }
            viewModel.onOnboardingCompleted()
            onFinish()
        },
        onBack    = onBack,
        skipLabel = if (!isIgnoring) "Atla" else null,
        onSkip    = if (!isIgnoring) {
            {
                viewModel.onOnboardingCompleted()
                onFinish()
            }
        } else null
    ) {
        if (!isIgnoring) {
            Spacer(Modifier.height(20.dp))
            // UNTESTED — verify PhIcons.Regular.Lightning exists
            StepInfoRow(
                icon = PhIcons.Regular.Lightning,
                text = "\"Hey IRIS\" arka planda çalışmaya devam eder"
            )
            Spacer(Modifier.height(8.dp))
            StepInfoRow(
                icon = PhIcons.Regular.ShieldCheck,
                text = "Pil kullanımı minimumda tutulur"
            )
        }
    }
}