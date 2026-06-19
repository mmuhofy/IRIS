package com.iris.assistant.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.ColorSurface
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.Gear
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.MicrophoneSlash
import com.phosphor.icons.regular.StopCircle
import com.phosphor.icons.regular.Television

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val primary = IrisTheme.colors.primary

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Top bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text  = "IRIS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text  = uiState.modelName
                                    .replace("-", " ")
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight    = FontWeight.Medium,
                                    letterSpacing = 0.3.sp
                                ),
                                color    = primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = uiState.statusText.lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Surface(
                    onClick         = onOpenSettings,
                    shape           = CircleShape,
                    color           = primary.copy(alpha = 0.12f),
                    modifier        = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            PhIcons.Regular.Gear,
                            contentDescription = "Ayarlar",
                            tint     = primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- Center: animation + status ---
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IrisCoreAnimation(
                    state       = uiState.coreState,
                    amplitude   = uiState.amplitude,
                    ttsProgress = uiState.ttsProgress
                )

                Spacer(Modifier.height(32.dp))

                AnimatedContent(
                    targetState  = uiState.statusText,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically(
                            animationSpec  = tween(250),
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
                        text  = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight    = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                uiState.captionText?.let { caption ->
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape    = RoundedCornerShape(14.dp),
                        color    = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    ) {
                        Text(
                            text      = caption,
                            style     = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                lineHeight = 22.sp
                            ),
                            color     = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // --- Permission dialog ---
            val permissionRequest = uiState.permissionRequest
            if (permissionRequest != null) {
                val isWriteSettings = permissionRequest.permission == "android.permission.WRITE_SETTINGS"
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> viewModel.onPermissionResult(granted) }
                val context = LocalContext.current

                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = ColorSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier            = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text  = "İzin Gerekli",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text  = permissionRequest.rationale,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick  = { viewModel.onPermissionResult(false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reddet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    if (isWriteSettings) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                        viewModel.onPermissionResult(true)
                                    } else {
                                        permLauncher.launch(permissionRequest.permission)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = primary,
                                    contentColor   = ColorTextPrimary
                                )
                            ) {
                                Text("İzin Ver")
                            }
                        }
                    }
                }
            }

            // --- Bottom control bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ControlButton(
                    icon      = if (uiState.isMuted) PhIcons.Regular.MicrophoneSlash
                                else PhIcons.Regular.Microphone,
                    label     = if (uiState.isMuted) "Sessiz" else "Mikrofon",
                    tint      = if (uiState.isMuted) MaterialTheme.colorScheme.onSurfaceVariant
                                else primary,
                    highlight = !uiState.isMuted,
                    pulse     = !uiState.isMuted && uiState.coreState == IrisCoreState.LISTENING,
                    onClick   = viewModel::onMicToggle
                )

                ControlButton(
                    icon      = PhIcons.Regular.StopCircle,
                    label     = "Durdur",
                    tint      = MaterialTheme.colorScheme.onSurfaceVariant,
                    highlight = false,
                    pulse     = false,
                    onClick   = viewModel::onStop
                )

                ControlButton(
                    icon      = PhIcons.Regular.Television,
                    label     = "Ekran",
                    tint      = if (uiState.isScreenCtrl) primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    highlight = uiState.isScreenCtrl,
                    pulse     = false,
                    onClick   = viewModel::onScreenControlToggle
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ControlButton — press scale for immediate tactile feedback
// ---------------------------------------------------------------------------
@Composable
private fun ControlButton(
    icon     : ImageVector,
    label    : String,
    tint     : Color,
    highlight: Boolean,
    pulse    : Boolean,
    onClick  : () -> Unit
) {
    val primary           = IrisTheme.colors.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()

    // Scale snaps down immediately on press, bounces back on release.
    // This gives instant visual feedback without waiting for coroutine dispatch.
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.87f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    // Genuine infinite pulse when listening (was tween → stuck at 1f, now loops)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.08f,
        targetValue   = 0.28f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier          = Modifier
                .size(56.dp)
                .scale(scale),   // scale wraps entire button + pulse ring together
            contentAlignment  = Alignment.Center
        ) {
            // Pulse ring — only rendered when listening
            if (pulse) {
                Surface(
                    shape    = CircleShape,
                    color    = primary.copy(alpha = pulseAlpha),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }

            // Button face
            Surface(
                onClick           = onClick,
                shape             = CircleShape,
                color             = if (highlight) primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface,
                shadowElevation   = if (highlight) 0.dp else 2.dp,
                modifier          = Modifier.size(56.dp),
                interactionSource = interactionSource   // shared — scale reacts to same press
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = label,
                        tint               = tint,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}