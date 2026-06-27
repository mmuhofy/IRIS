package com.iris.assistant.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.clip
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
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*

// ---------------------------------------------------------------------------
// HomeScreen
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var lastCaption by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.captionText) {
        uiState.captionText?.let { lastCaption = it }
    }

    // See IrisNavGraph.kt comment — containerColor is transparent so the
    // background Box in NavHost paints through during nav transitions.
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ------------------------------------------------------------------
            // Top bar
            // ------------------------------------------------------------------
            HomeTopBar(
                modelName = uiState.modelName,
                llmProvider = uiState.llmProvider,
                dropdownExpanded = modelDropdownExpanded,
                onDropdownExpandChange = { modelDropdownExpanded = it },
                onModelChange = { model ->
                    viewModel.onLlmModelChange(model)
                    modelDropdownExpanded = false
                },
                onMenuClick = onOpenDrawer,
                onSettingsClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            // ------------------------------------------------------------------
            // Center — AmbientGlow + IrisCoreAnimation (untouched) + status
            // ------------------------------------------------------------------
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // AmbientGlow is a decorative layer only — IrisCoreAnimation.kt untouched
                    AmbientGlow(state = uiState.coreState)
                    IrisCoreAnimation(
                        state = uiState.coreState,
                        amplitude = uiState.amplitude,
                        ttsProgress = uiState.ttsProgress
                    )
                }

                Spacer(Modifier.height(32.dp))

                StateLabel(
                    coreState = uiState.coreState,
                    statusText = uiState.statusText
                )

                Spacer(Modifier.height(20.dp))

                AnimatedVisibility(
                    visible = uiState.captionText != null,
                    enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.94f, animationSpec = tween(250)),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150))
                ) {
                    CaptionBubble(text = lastCaption)
                }
            }

            // ------------------------------------------------------------------
            // Permission dialog
            // ------------------------------------------------------------------
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
                                                    data = Uri.parse("package:" + context.packageName)
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

            // ------------------------------------------------------------------
            // Bottom dock
            // ------------------------------------------------------------------
            ControlDock(
                isMuted = uiState.isMuted,
                isListening = uiState.coreState == IrisCoreState.LISTENING,
                isScreenCtrl = uiState.isScreenCtrl,
                onMicToggle = viewModel::onMicToggle,
                onStop = viewModel::onStop,
                onScreenControlToggle = viewModel::onScreenControlToggle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 36.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// HomeTopBar
// ---------------------------------------------------------------------------

@Composable
private fun HomeTopBar(
    modelName: String,
    llmProvider: String,
    dropdownExpanded: Boolean,
    onDropdownExpandChange: (Boolean) -> Unit,
    onModelChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = IrisTheme.colors.primary

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Menu icon
        TopBarIconButton(
            icon = PhIcons.Regular.List,
            contentDescription = "Menu",
            onClick = onMenuClick
        )

        // Model selector chip + dropdown
        Box {
            ModelChip(
                modelName = modelName,
                expanded = dropdownExpanded,
                onClick = { onDropdownExpandChange(true) }
            )

            val models = Constants.modelsForProvider(llmProvider)
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { onDropdownExpandChange(false) },
                shape = RoundedCornerShape(16.dp),
            ) {
                models.forEach { model ->
                    val isSelected = model.apiName == modelName
                    Surface(
                        onClick = { onModelChange(model.apiName) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = PhIcons.Regular.Check,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings icon
        TopBarIconButton(
            icon = PhIcons.Regular.Gear,
            contentDescription = "Ayarlar",
            onClick = onSettingsClick
        )
    }
}

// ---------------------------------------------------------------------------
// ModelChip — pill-shaped model selector button
// ---------------------------------------------------------------------------

@Composable
private fun ModelChip(
    modelName: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val primary = IrisTheme.colors.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val chipBgAlpha by animateFloatAsState(
        targetValue = if (pressed || expanded) 0.14f else 0.07f,
        animationSpec = tween(120),
        label = "chipBg"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(120),
        label = "chipScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = primary.copy(alpha = chipBgAlpha),
        interactionSource = interactionSource,
        modifier = Modifier.scale(pressScale)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                text = modelName
                    .replace("-", " ")
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = PhIcons.Regular.CaretDown,
                contentDescription = "Model değiştir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// TopBarIconButton — minimal icon button with scale press feedback
// ---------------------------------------------------------------------------

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(120),
        label = "iconBtnScale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(pressScale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// StateLabel — pulsing dot + animated status text
// ---------------------------------------------------------------------------

@Composable
private fun StateLabel(
    coreState: IrisCoreState,
    statusText: String
) {
    val primary = IrisTheme.colors.primary

    val dotTargetColor = when (coreState) {
        IrisCoreState.IDLE -> ColorTextSecondary.copy(alpha = 0.45f)
        else -> primary
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stateDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    // Only pulse when non-idle
    val effectiveDotColor = when (coreState) {
        IrisCoreState.IDLE -> dotTargetColor
        else -> primary.copy(alpha = dotAlpha)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = effectiveDotColor, shape = CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        AnimatedContent(
            targetState = statusText,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically(
                    animationSpec = tween(250),
                    initialOffsetY = { it / 4 }
                )) togetherWith (fadeOut(tween(150)) + slideOutVertically(
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
    }
}

// ---------------------------------------------------------------------------
// CaptionBubble — live TTS caption card
// ---------------------------------------------------------------------------

@Composable
private fun CaptionBubble(text: String) {
    val primary = IrisTheme.colors.primary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(primary.copy(alpha = 0.28f), primary.copy(alpha = 0.04f))
            )
        ),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 22.sp
            ),
            color = Color.White.copy(alpha = 0.90f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// AmbientGlow — decorative radial gradient behind IrisCoreAnimation.
// IrisCoreAnimation.kt is NOT modified. This composable is unchanged.
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

@Composable
private fun ControlDock(
    isMuted: Boolean,
    isListening: Boolean,
    isScreenCtrl: Boolean,
    onMicToggle: () -> Unit,
    onStop: () -> Unit,
    onScreenControlToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockPill(
                icon = PhIcons.Regular.Television,
                label = "Ekran",
                isActive = isScreenCtrl,
                onClick = onScreenControlToggle,
                modifier = Modifier.weight(1f),
            )

            DockPill(
                icon = if (isMuted) PhIcons.Regular.MicrophoneSlash else PhIcons.Regular.Microphone,
                label = if (isMuted) "Sessiz" else "Mikrofon",
                isActive = !isMuted,
                isListening = isListening,
                useGradient = !isMuted,
                primary = primary,
                gradientEnd = gradientEnd,
                onClick = onMicToggle,
                modifier = Modifier.weight(1.3f),
            )

            DockPill(
                icon = PhIcons.Regular.StopCircle,
                label = "Durdur",
                isActive = false,
                onClick = onStop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    useGradient: Boolean = false,
    primary: Color = Color.Unspecified,
    gradientEnd: Color = Color.Unspecified,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "dockPillScale",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "dockPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dockPulseAlpha",
    )

    val bgColor = when {
        useGradient -> Color.Transparent
        isActive -> primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .scale(pressScale)
            .then(
                if (useGradient) {
                    Modifier.background(
                        brush = Brush.linearGradient(listOf(primary, gradientEnd)),
                        shape = RoundedCornerShape(24.dp),
                    )
                } else {
                    Modifier.background(color = bgColor, shape = RoundedCornerShape(24.dp))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isListening && useGradient) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = Color.White.copy(alpha = pulseAlpha),
                            shape = CircleShape,
                        ),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive || useGradient) Color.White
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (isActive || useGradient) Color.White.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}