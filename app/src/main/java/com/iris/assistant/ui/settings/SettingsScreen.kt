package com.iris.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.components.IrisButtonDestructive
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants

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
                            PhIcons.Regular.ArrowLeft,
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
            Spacer(Modifier.height(8.dp))

            // --- Renk Teması ---
            SettingsSectionTitle("Renk Teması")
            Spacer(Modifier.height(10.dp))
            Card(
                shape  = RoundedCornerShape(Constants.CARD_CORNER_RADIUS.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ColorSchemeSelector(
                        current  = uiState.colorScheme,
                        onChange = viewModel::onColorSchemeChange
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Ses Karakteri ---
            SettingsSectionTitle("Ses Karakteri")
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "IRIS'in sesi için bir karakter seç",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Card(
                shape  = RoundedCornerShape(Constants.CARD_CORNER_RADIUS.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    VoiceSelector(
                        current  = uiState.ttsVoice,
                        onChange = viewModel::onTtsVoiceChange
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Arka Plan ---
            SettingsSectionTitle("Arka Plan")
            Spacer(Modifier.height(10.dp))
            Card(
                shape  = RoundedCornerShape(Constants.CARD_CORNER_RADIUS.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SettingsToggleRow(
                        icon     = PhIcons.Regular.Headphones,
                        title    = "Arka planda dinle",
                        subtitle = "IRIS kapalıyken de \"Hey IRIS\" dinler",
                        checked  = uiState.backgroundListening,
                        onChange = viewModel::onBackgroundListeningChange
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Veri ---
            SettingsSectionTitle("Veri")
            Spacer(Modifier.height(10.dp))
            Card(
                shape  = RoundedCornerShape(Constants.CARD_CORNER_RADIUS.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    IrisButtonDestructive(
                        text     = "Sohbet Geçmişini Temizle",
                        onClick  = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Voice selector — grid of selectable voice chips
// ---------------------------------------------------------------------------
@Composable
private fun VoiceSelector(
    current : TtsVoice,
    onChange: (TtsVoice) -> Unit
) {
    val voices = TtsVoice.entries
    val rows = voices.chunked(2)

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { voice ->
                    VoiceChip(
                        voice      = voice,
                        isSelected = voice == current,
                        onClick    = { onChange(voice) },
                        modifier   = Modifier
                            .weight(1f)
                            .padding(end = if (voice == row.last()) 0.dp else 8.dp)
                            .padding(bottom = 8.dp)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VoiceChip(
    voice     : TtsVoice,
    isSelected: Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val primary = IrisTheme.colors.primary
    Surface(
        onClick      = onClick,
        shape        = RoundedCornerShape(12.dp),
        color        = if (isSelected) primary.copy(alpha = 0.15f)
                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border       = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) primary
                    else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier     = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text  = voice.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) primary
                        else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text  = voice.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                onClick  = { onChange(scheme) },
                shape    = CircleShape,
                color    = irisColors.primary,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(if (isSelected) 38.dp else 30.dp),
                border   = if (isSelected)
                    BorderStroke(2.dp, Color.White)
                else null
            ) {}
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable toggle row with icon
// ---------------------------------------------------------------------------
@Composable
private fun SettingsToggleRow(
    icon    : ImageVector,
    title   : String,
    subtitle: String,
    checked : Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(22.dp),
            tint               = IrisTheme.colors.primary
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = IrisTheme.colors.primary
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
        style = MaterialTheme.typography.titleSmall,
        color = IrisTheme.colors.primary
    )
}
