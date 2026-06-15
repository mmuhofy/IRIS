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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.IrisTheme

@Composable
fun HomeScreen(
    onOpenChat    : () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel     : HomeViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Register wake word BroadcastReceiver while HomeScreen is in composition.
    // DisposableEffect fires on entry; onDispose fires when HomeScreen leaves composition.
    // This keeps receiver tied to screen lifecycle — no leak risk.
    DisposableEffect(Unit) {
        viewModel.registerWakeWordReceiver()
        onDispose { viewModel.unregisterWakeWordReceiver() }
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
                IconButton(onClick = { /* TODO: drawer (Phase 2) */ }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menü",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text  = "IRIS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row {
                    IconButton(onClick = onOpenChat) {
                        Icon(Icons.Outlined.Chat, contentDescription = "Sohbet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // --- Center: Iris Core + status text ---
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IrisCoreAnimation(
                    state       = uiState.coreState,
                    amplitude   = uiState.amplitude,
                    ttsProgress = uiState.ttsProgress
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text      = uiState.statusText,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // --- Permission request dialog ---
            val permissionRequest = uiState.permissionRequest
            if (permissionRequest != null) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    viewModel.onPermissionResult(granted)
                }
                AlertDialog(
                    onDismissRequest = { viewModel.onPermissionResult(false) },
                    title            = { Text("İzin Gerekli") },
                    text             = { Text(permissionRequest.rationale) },
                    confirmButton = {
                        TextButton(onClick = { launcher.launch(permissionRequest.permission) }) {
                            Text("İzin Ver")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onPermissionResult(false) }) {
                            Text("Reddet")
                        }
                    }
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
                IconButton(onClick = viewModel::onMicToggle) {
                    Icon(
                        imageVector        = if (uiState.isMuted) Icons.Outlined.MicOff
                                             else Icons.Outlined.Mic,
                        contentDescription = if (uiState.isMuted) "Mikrofon kapalı" else "Mikrofon açık",
                        tint               = if (uiState.isMuted) MaterialTheme.colorScheme.onSurfaceVariant
                                             else IrisTheme.colors.primary
                    )
                }
                IconButton(onClick = viewModel::onStop) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Durdur",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = viewModel::onScreenControlToggle) {
                    Icon(
                        imageVector        = Icons.Outlined.Tv,
                        contentDescription = "Ekran kontrolü",
                        tint               = if (uiState.isScreenCtrl) IrisTheme.colors.primary
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}