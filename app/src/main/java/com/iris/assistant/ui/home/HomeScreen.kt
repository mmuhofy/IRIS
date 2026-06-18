package com.iris.assistant.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import com.phosphor.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.IrisTheme

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel     : HomeViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState =  remember { SnackbarHostState() }
    val primary           =  IrisTheme.colors.primary

    // Start wake word detection when screen is visible, stop when hidden
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
                modifier              = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Empty space to balance the right side icons
                Box(Modifier.size(48.dp))

                Row {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            PhIcons.Regular.Gear,
                            contentDescription = "Ayarlar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                Spacer(Modifier.height(28.dp))
                Text(
                    text      = uiState.statusText,
                    style     = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight   = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    ),
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // --- Permission dialog ---
            val permissionRequest = uiState.permissionRequest
            if (permissionRequest != null) {
                val isWriteSettings = permissionRequest.permission == "android.permission.WRITE_SETTINGS"
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> viewModel.onPermissionResult(granted) }
                val context = LocalContext.current

                AlertDialog(
                    onDismissRequest = { viewModel.onPermissionResult(false) },
                    title   = { Text("İzin Gerekli") },
                    text    = { Text(permissionRequest.rationale) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (isWriteSettings) {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
                                    ).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                )
                                viewModel.onPermissionResult(true)
                            } else {
                                permLauncher.launch(permissionRequest.permission)
                            }
                        }) { Text("İzin Ver") }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onPermissionResult(false) }) {
                            Text("Reddet") }
                    }
                )
            }

            // --- Bottom controls: 3 separate round buttons ---
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Mute / Unmute
                ControlButton(
                    icon      = if (uiState.isMuted) PhIcons.Regular.MicrophoneSlash else PhIcons.Regular.Microphone,
                    label     = if (uiState.isMuted) "Sessiz" else "Mikrofon",
                    tint      = if (uiState.isMuted) MaterialTheme.colorScheme.onSurfaceVariant
                                else primary,
                    highlight = !uiState.isMuted,
                    onClick   = viewModel::onMicToggle
                )

                // Stop
                ControlButton(
                    icon      = PhIcons.Regular.StopCircle,
                    label     = "Durdur",
                    tint      = MaterialTheme.colorScheme.onSurfaceVariant,
                    highlight = false,
                    onClick   = viewModel::onStop
                )

                // Screen control
                ControlButton(
                    icon      = PhIcons.Regular.Television,
                    label     = "Ekran",
                    tint      = if (uiState.isScreenCtrl) primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    highlight = uiState.isScreenCtrl,
                    onClick   = viewModel::onScreenControlToggle
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Round control button with label
// ---------------------------------------------------------------------------
@Composable
private fun ControlButton(
    icon     : ImageVector,
    label    : String,
    tint     : Color,
    highlight: Boolean,
    onClick  : () -> Unit
) {
    val primary = IrisTheme.colors.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape   = CircleShape,
            color   = if (highlight) primary.copy(alpha = 0.15f)
                      else MaterialTheme.colorScheme.surface,
            shadowElevation = if (highlight) 0.dp else 2.dp,
            modifier = Modifier.size(56.dp)
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
        Spacer(Modifier.height(6.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
