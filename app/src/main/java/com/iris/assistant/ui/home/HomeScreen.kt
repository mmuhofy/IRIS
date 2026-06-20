package com.iris.assistant.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.components.IrisButtonPrimary
import com.iris.assistant.ui.components.IrisButtonSecondary
import com.iris.assistant.ui.components.IrisCard
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // -----------------------------------------------------------------
            // Top bar — gradient wordmark + model/status pill, settings button
            // -----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "IRIS",
                        // NOTE: TextStyle.brush requires Compose UI >= 1.4.0.
                        // Project Compose BOM is 2026.04.01 (Material3 1.4.0, per
                        // gradle/libs.versions.toml) — well above this minimum, safe to use.
                        style = MaterialTheme.typography.titleLarge.copy(
                            brush = Brush.linearGradient(listOf(primary, gradientEnd)),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            StatusDot(state = uiState.coreState)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = uiState.modelName
                                    .replace("-", " ")
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            AnimatedContent(
                                targetState = uiState.statusText,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                                },
                                label = "headerStatus"
                            ) { text ->
                                Text(
                                    text = text.lowercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                val settingsInteraction = remember { MutableInteractionSource() }
                val settingsPressed by settingsInteraction.collectIsPressedAsState()
                val settingsScale by animateFloatAsState(
                    targetValue = if (settingsPressed) 0.90f else 1f,
                    animationSpec = tween(120),
                    label = "settingsScale"
                )

                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.12f),
                    interactionSource = settingsInteraction,
                    modifier = Modifier
                        .size(44.dp)
                        .scale(settingsScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            PhIcons.Regular.Gear,
                            contentDescription = "Ayarlar",
                            tint = primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // Center — ambient glow (decorative) + Iris Core Animation (untouched)
            // -----------------------------------------------------------------
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Decorative depth layer only — does not call into or modify
                    // IrisCoreAnimation.kt. The core animation itself is untouched.
                    AmbientGlow(state = uiState.coreState)
                    IrisCoreAnimation(
                        state = uiState.coreState,
                        amplitude = uiState.amplitude,
                        ttsProgress = uiState.ttsProgress
                    )
                }

                Spacer(Modifier.height(28.dp))

                AnimatedContent(
                    targetState = uiState.statusText,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically(
                            animationSpec = tween(250),
                            initialOffsetY = { it / 4 }
                        )) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically(
                                animationSpec = tween(150),
                                targetOffsetY = { -it / 4 }
                            ))
                    },
                    label = "statusText"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(16.dp))

                var lastCaption by remember { mutableStateOf("") }
                LaunchedEffect(uiState.captionText) {
                    uiState.captionText?.let { lastCaption = it }
                }

                AnimatedVisibility(
                    visible = uiState.captionText != null,
                    enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.94f, animationSpec = tween(250)),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150))
                ) {
                    CaptionBubble(text = lastCaption)
                }
            }

            // -----------------------------------------------------------------
            // Permission dialog — scrim + IrisCard, reuses shared button components
            // -----------------------------------------------------------------
            val permissionRequest = uiState.permissionRequest
            var lastPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
            LaunchedEffect(permissionRequest) {
                permissionRequest?.let { lastPermissionRequest = it }
            }

            AnimatedVisibility(
                visible = permissionRequest != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    val dialogData = lastPermissionRequest
                    if (dialogData != null) {
                        val isWriteSettings =
                            dialogData.permission == "android.permission.WRITE_SETTINGS"
                        val permLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted -> viewModel.onPermissionResult(granted) }
                        val context = LocalContext.current

                        IrisCard(
                            modifier = Modifier.padding(24.dp),
                            elevation = 12.dp,
                            innerPadding = 24.dp
                        ) {
                            Text(
                                text = "İzin Gerekli",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = dialogData.rationale,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IrisButtonSecondary(
                                    text = "Reddet",
                                    onClick = { viewModel.onPermissionResult(false) },
                                    modifier = Modifier.weight(1f)
                                )
                                IrisButtonPrimary(
                                    text = "İzin Ver",
                                    onClick = {
                                        if (isWriteSettings) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            )
                                            viewModel.onPermissionResult(true)
                                        } else {
                                            permLauncher.launch(dialogData.permission)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // Bottom dock — Mic / Stop / Screen-control, always visible
            // -----------------------------------------------------------------
            ControlDock(
                isMuted = uiState.isMuted,
                isListening = uiState.coreState == IrisCoreState.LISTENING,
                isScreenCtrl = uiState.isScreenCtrl,
                onMicToggle = viewModel::onMicToggle,
                onStop = viewModel::onStop,
                onScreenControlToggle = viewModel::onScreenControlToggle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp, vertical = 28.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// StatusDot — small animated indicator reflecting IrisCoreState
// ---------------------------------------------------------------------------
@Composable
private fun StatusDot(state: IrisCoreState, modifier: Modifier = Modifier) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val secondary = IrisTheme.colors.secondary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    val dotColor by animateColorAsState(
        targetValue = when (state) {
            IrisCoreState.IDLE -> idleColor
            IrisCoreState.LISTENING -> gradientEnd
            IrisCoreState.THINKING -> primary
            IrisCoreState.SPEAKING -> secondary
        },
        animationSpec = tween(300),
        label = "statusDotColor"
    )

    Box(
        modifier = modifier
            .size(7.dp)
            .background(dotColor, CircleShape)
    )
}

// ---------------------------------------------------------------------------
// AmbientGlow — decorative depth layer behind IrisCoreAnimation.
// Lives entirely in HomeScreen.kt; IrisCoreAnimation.kt is not modified.
// ---------------------------------------------------------------------------
@Composable
private fun AmbientGlow(state: IrisCoreState, modifier: Modifier = Modifier) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    val targetAlpha = when (state) {
        IrisCoreState.IDLE -> 0.10f
        IrisCoreState.LISTENING -> 0.20f
        IrisCoreState.THINKING -> 0.24f
        IrisCoreState.SPEAKING -> 0.18f
    }
    val glowAlpha by animateFloatAsState(targetAlpha, tween(500), label = "ambientGlowAlpha")

    Box(
        modifier = modifier
            .size((Constants.IRIS_CORE_SIZE + 140).dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = glowAlpha),
                        gradientEnd.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}

// ---------------------------------------------------------------------------
// CaptionBubble — live TTS caption surface
// ---------------------------------------------------------------------------
@Composable
private fun CaptionBubble(text: String) {
    val primary = IrisTheme.colors.primary

    Surface(
        shape = RoundedCornerShape(Constants.CARD_CORNER_RADIUS.dp),
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(primary.copy(alpha = 0.35f), Color.Transparent)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 22.sp
            ),
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// ControlDock — floating surface housing the three always-visible controls
// ---------------------------------------------------------------------------
@Composable
private fun ControlDock(
    isMuted: Boolean,
    isListening: Boolean,
    isScreenCtrl: Boolean,
    onMicToggle: () -> Unit,
    onStop: () -> Unit,
    onScreenControlToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = IrisTheme.colors.primary

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = if (isMuted) PhIcons.Regular.MicrophoneSlash else PhIcons.Regular.Microphone,
                label = if (isMuted) "Sessiz" else "Mikrofon",
                tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else primary,
                highlight = !isMuted,
                pulse = !isMuted && isListening,
                onClick = onMicToggle
            )

            ControlButton(
                icon = PhIcons.Regular.StopCircle,
                label = "Durdur",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                highlight = false,
                pulse = false,
                onClick = onStop
            )

            ControlButton(
                icon = PhIcons.Regular.Television,
                label = "Ekran",
                tint = if (isScreenCtrl) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                highlight = isScreenCtrl,
                pulse = false,
                onClick = onScreenControlToggle
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ControlButton — single dock action with pulse + press feedback
// ---------------------------------------------------------------------------
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    highlight: Boolean,
    pulse: Boolean,
    onClick: () -> Unit
) {
    val primary = IrisTheme.colors.primary
    val pulseAnim by animateFloatAsState(
        targetValue = if (pulse) 1f else 0f,
        animationSpec = tween(600),
        label = "pulse"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(120),
        label = "controlButtonPressScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (pulse) {
                Surface(
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.15f * pulseAnim),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (highlight) primary.copy(alpha = 0.15f * (1f + 0.3f * pulseAnim))
                else MaterialTheme.colorScheme.surface,
                shadowElevation = if (highlight) 0.dp else 2.dp,
                interactionSource = interactionSource,
                modifier = Modifier
                    .size(56.dp)
                    .scale(pressScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}