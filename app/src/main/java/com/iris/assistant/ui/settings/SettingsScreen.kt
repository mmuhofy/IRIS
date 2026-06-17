package com.iris.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.components.IrisButtonDestructive
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.ColorSurface
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*

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

            // --- Ses Karakteri ---
            SectionHeader(title = "Ses Karakteri", subtitle = "IRIS'in sesi için bir karakter seç")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                VoiceSelector(
                    current  = uiState.ttsVoice,
                    onChange = viewModel::onTtsVoiceChange
                )
            }

            Spacer(Modifier.height(24.dp))

            // --- Yapay Zeka Modeli ---
            SectionHeader(title = "Yapay Zeka Modeli", subtitle = "IRIS'in kullandığı dil modeli ve sağlayıcı")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                ProviderSelector(
                    current  = uiState.llmProvider,
                    onChange = viewModel::onLlmProviderChange
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    color = ColorTextSecondary.copy(alpha = 0.2f)
                )
                ModelSelector(
                    current       = uiState.llmModel,
                    provider      = uiState.llmProvider,
                    onChange      = viewModel::onLlmModelChange
                )
            }

            Spacer(Modifier.height(24.dp))

            // --- Renk Teması ---
            SectionHeader(title = "Renk Teması", subtitle = "IRIS'in görünümünü kişiselleştir")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                ColorSchemeSelector(
                    current  = uiState.colorScheme,
                    onChange = viewModel::onColorSchemeChange
                )
            }

            Spacer(Modifier.height(24.dp))

            // --- Arka Plan ---
            SectionHeader(title = "Arka Plan", subtitle = "Uygulama kapalıyken davranış")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                SettingsToggleRow(
                    icon     = PhIcons.Regular.Headphones,
                    title    = "Arka planda dinle",
                    subtitle = "IRIS kapalıyken de \"Hey IRIS\" dinler",
                    checked  = uiState.backgroundListening,
                    onChange = viewModel::onBackgroundListeningChange
                )
            }

            Spacer(Modifier.height(24.dp))

            // --- Veri ---
            SectionHeader(title = "Veri", subtitle = "Depolanan sohbet geçmişini yönet")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                IrisButtonDestructive(
                    text     = "Sohbet Geçmişini Temizle",
                    onClick  = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Section header — Apple-style with optional subtitle
// ---------------------------------------------------------------------------
@Composable
private fun SectionHeader(
    title   : String,
    subtitle: String? = null
) {
    Column(modifier = Modifier.padding(start = 4.dp)) {
        Text(
            text  = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = IrisTheme.colors.primary,
            fontWeight = FontWeight.SemiBold
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Settings card — Apple-style grouped card
// ---------------------------------------------------------------------------
@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(Constants.CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
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
                       else Color.Transparent,
        border       = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) primary
                    else ColorTextSecondary.copy(alpha = 0.3f)
        ),
        modifier     = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text  = voice.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) primary
                        else ColorTextPrimary
            )
            Text(
                text  = voice.description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary
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
// Provider selector — two pill buttons
// ---------------------------------------------------------------------------
@Composable
private fun ProviderSelector(
    current : String,
    onChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Constants.LLM_PROVIDERS.forEachIndexed { index, provider ->
            val isSelected = provider == current
            Surface(
                onClick  = { onChange(provider) },
                shape    = RoundedCornerShape(12.dp),
                color    = if (isSelected) IrisTheme.colors.primary
                           else Color.Transparent,
                border   = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) IrisTheme.colors.primary
                            else ColorTextSecondary.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (index == 0) 0.dp else 6.dp,
                        end  = if (index == Constants.LLM_PROVIDERS.lastIndex) 0.dp else 6.dp
                    )
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = PhIcons.Regular.Circle,
                        contentDescription = null,
                        modifier           = Modifier.size(12.dp),
                        tint               = if (isSelected) Color.White
                                              else ColorTextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = Constants.providerDisplayName(provider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) Color.White
                                else ColorTextPrimary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Model selector — dropdown filtered by provider
// ---------------------------------------------------------------------------
@Composable
private fun ModelSelector(
    current : String,
    provider: String,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val models = Constants.modelsForProvider(provider)

    val selectedModel = models.find { it.apiName == current }

    Box {
        Surface(
            onClick  = { expanded = true },
            shape    = RoundedCornerShape(12.dp),
            color    = Color.Transparent,
            border   = BorderStroke(1.dp, ColorTextSecondary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Model",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = selectedModel?.displayName ?: current,
                        style = MaterialTheme.typography.bodyLarge,
                        color = IrisTheme.colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector        = PhIcons.Regular.CaretDown,
                    contentDescription = null,
                    tint               = ColorTextSecondary,
                    modifier           = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (model.apiName == current) {
                                Icon(
                                    imageVector        = PhIcons.Regular.Check,
                                    contentDescription = null,
                                    modifier           = Modifier.size(16.dp),
                                    tint               = IrisTheme.colors.primary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text  = model.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (model.apiName == current) IrisTheme.colors.primary
                                            else ColorTextPrimary,
                                    fontWeight = if (model.apiName == current) FontWeight.Medium
                                                 else FontWeight.Normal
                                )
                                Text(
                                    text  = model.apiName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorTextSecondary
                                )
                        }
                    }
                },
                    onClick = {
                        onChange(model.apiName)
                        expanded = false
                    }
                )
            }
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
        modifier          = Modifier.fillMaxWidth(),
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
                color = ColorTextPrimary
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary
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
