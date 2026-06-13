package com.iris.assistant.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.components.IrisButtonDestructive
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.IrisTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack   : () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title   = { Text("Geçmişi Temizle") },
            text    = { Text("Tüm sohbet geçmişi silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearHistory()
                    showClearDialog = false
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("İptal") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // --- Renk Teması ---
            SettingsSectionTitle("Renk Teması")
            Spacer(Modifier.height(12.dp))
            ColorSchemeSelector(
                current  = uiState.colorScheme,
                onChange = viewModel::onColorSchemeChange
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(24.dp))

            // --- Arka Plan ---
            SettingsSectionTitle("Arka Plan")
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                title    = "Arka planda dinle",
                subtitle = "IRIS kapalıyken de \"Hey IRIS\" dinler",
                checked  = uiState.backgroundListening,
                onChange = viewModel::onBackgroundListeningChange
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(24.dp))

            // --- Veri ---
            SettingsSectionTitle("Veri")
            Spacer(Modifier.height(12.dp))
            IrisButtonDestructive(
                text    = "Sohbet Geçmişini Temizle",
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Color scheme selector — row of colored circles
// ---------------------------------------------------------------------------
@Composable
private fun ColorSchemeSelector(
    current : ColorSchemeOption,
    onChange: (ColorSchemeOption) -> Unit
) {
    val schemes = ColorSchemeOption.entries
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        schemes.forEach { scheme ->
            val irisColors = scheme.toIrisColorScheme()
            val isSelected = scheme == current
            Surface(
                onClick = { onChange(scheme) },
                shape   = CircleShape,
                color   = irisColors.primary,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(if (isSelected) 38.dp else 30.dp),
                border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                else null
            ) {}
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable toggle row
// ---------------------------------------------------------------------------
@Composable
private fun SettingsToggleRow(
    title   : String,
    subtitle: String,
    checked : Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = MaterialTheme.colorScheme.background,
                checkedTrackColor  = IrisTheme.colors.primary
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Section title
// ---------------------------------------------------------------------------
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = IrisTheme.colors.primary
    )
}