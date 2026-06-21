package com.iris.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.components.IrisButtonDestructive
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack                  : () -> Unit,
    onOpenLocalModels       : () -> Unit = {},
    onOpenPermissionManager : () -> Unit = {},
    onOpenVoiceSettings     : () -> Unit = {},
    viewModel        : SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // NOTE: SettingsViewModel.uiState is backed by stateIn(initialValue =
    // SettingsUiState()) — the data class's DEFAULT values (Lavender, SAFE,
    // Gemini, etc), shown for one or more frames until the first real
    // DataStore emission arrives. Combined with WhileSubscribed(5_000),
    // re-entering this screen after being away 5+ seconds restarts the
    // collector and replays this default-then-real flash. Root cause
    // confirmed by Muhofy: every Settings entry briefly shows defaults
    // before snapping to the actual saved values.
    //
    // Fix: gate rendering on a `loaded` flag that flips true only once a
    // real DataStore value has been observed (see SettingsViewModel.kt).
    // Until then, render nothing (Box takes the full Scaffold content area,
    // matching background — no visible flash, no spinner needed since this
    // is typically sub-frame on local DataStore reads).
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
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

    // NOTE: containerColor (Scaffold AND TopAppBar) changed from
    // MaterialTheme.colorScheme.background to Color.Transparent. Same root
    // cause as HomeScreen.kt: an opaque background here was painting over
    // the exiting screen during IrisNavGraph.kt's scale+fade transition,
    // hiding the animation entirely (confirmed against Peristyle's Home.kt
    // reference). The real background color now lives once, in the Box
    // wrapping NavHost in IrisNavGraph.kt. Nothing else in this file changed.
    // NOTE: containerColor (Scaffold AND TopAppBar) is Color.Transparent, not
    // MaterialTheme.colorScheme.background. Root cause: an opaque background
    // here paints over the exiting screen during IrisNavGraph.kt's scale+fade
    // transition, hiding the animation entirely (confirmed against
    // Peristyle's Home.kt reference). The real background color lives once,
    // in the Box wrapping NavHost in IrisNavGraph.kt. Do NOT revert this to
    // opaque "for performance" — that was tried once already and silently
    // broke every nav transition in the app.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
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
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        if (!loaded) {
            // Real DataStore value not observed yet — render nothing rather
            // than the SettingsUiState() defaults. This is normally
            // sub-frame on a local DataStore read, so no spinner is shown;
            // the background Box (from IrisNavGraph.kt) is already visible
            // underneath, so this just looks like a brief continuation of
            // the nav transition rather than a flash of wrong content.
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Ses ────────────────────────────────────────────────────────────
            SettingsGroup(title = "Ses") {
                SettingsRowWithContent(
                    icon = PhIcons.Regular.Waveform,
                    label = "Ses karakteri",
                ) {
                    VoiceSelector(
                        current  = uiState.ttsVoice,
                        onChange = viewModel::onTtsVoiceChange,
                    )
                }
                SettingsGroupDivider()
                SettingsRowWithContent(
                    icon = PhIcons.Regular.Robot,
                    label = "TTS sağlayıcısı",
                ) {
                    TtsProviderSelector(
                        current  = uiState.ttsProvider,
                        onChange = viewModel::onTtsProviderChange,
                    )
                }
            }

            // ── Model ──────────────────────────────────────────────────────────
            SettingsGroup(title = "Model") {
                ProviderSelector(
                    current  = uiState.llmProvider,
                    onChange = viewModel::onLlmProviderChange,
                )
                if (uiState.llmProvider != Constants.LLM_PROVIDER_LOCAL) {
                    SettingsGroupDivider()
                    ModelSelector(
                        current       = uiState.llmModel,
                        provider      = uiState.llmProvider,
                        onChange      = viewModel::onLlmModelChange,
                    )
                } else {
                    SettingsGroupDivider()
                    SettingsTappableRow(
                        icon = PhIcons.Regular.Download,
                        label = "Yerel Model",
                        description = uiState.localModelName.ifBlank { "Henüz seçilmedi" },
                        onClick = onOpenLocalModels,
                    )
                }
            }

            // ── Görünüm ────────────────────────────────────────────────────────
            SettingsGroup(title = "Görünüm") {
                SettingsRowWithContent(
                    icon = PhIcons.Regular.Palette,
                    label = "Renk teması",
                ) {
                    ColorSchemeSelector(
                        current  = uiState.colorScheme,
                        onChange = viewModel::onColorSchemeChange,
                    )
                }
            }

            // ── Arka Plan ──────────────────────────────────────────────────────
            SettingsGroup(title = "Arka Plan") {
                SettingsSwitchRow(
                    icon = PhIcons.Regular.Headphones,
                    label = "Arka planda dinle",
                    description = "IRIS kapalıyken de \"Hey IRIS\" dinler",
                    checked = uiState.backgroundListening,
                    onCheckedChange = viewModel::onBackgroundListeningChange,
                )
            }

            // ── Otonomi ────────────────────────────────────────────────────────
            SettingsGroup(title = "Otonomi") {
                SettingsRowWithContent(
                    icon = PhIcons.Regular.Shield,
                    label = "Otonomi seviyesi",
                    description = uiState.autonomyLevel.let { level ->
                        when (level) {
                            AutonomyLevel.SAFE      -> "3sn önizleme, yıkıcı işlemler için onay"
                            AutonomyLevel.BALANCED  -> "1sn önizleme, yıkıcı işlemler için onay"
                            AutonomyLevel.FULL_AUTO -> "Önizlemesiz, tüm işlemler otomatik"
                        }
                    },
                ) {
                    AutonomyLevelSelector(
                        current  = uiState.autonomyLevel,
                        onChange = viewModel::onAutonomyLevelChange,
                    )
                }
            }

            // ── Sistem ─────────────────────────────────────────────────────────
            SettingsGroup(title = "Sistem") {
                SettingsTappableRow(
                    icon = PhIcons.Regular.SpeakerHigh,
                    label = "Ses ayarları",
                    description = "Ses karakteri, sağlayıcı seçimi",
                    onClick = onOpenVoiceSettings,
                )
                SettingsGroupDivider()
                SettingsTappableRow(
                    icon = PhIcons.Regular.Lock,
                    label = "İzin yöneticisi",
                    description = "Tüm uygulama izinlerini görüntüle",
                    onClick = onOpenPermissionManager,
                )
            }

            // ── Veri ───────────────────────────────────────────────────────────
            SettingsGroup(title = "Veri") {
                SettingsTappableRow(
                    icon = PhIcons.Regular.Trash,
                    label = "Sohbet geçmişini temizle",
                    description = "Tüm konuşmalar silinir, geri alınamaz",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showClearDialog = true },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Group card ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = IrisTheme.colors.primary,
            letterSpacing = TextUnit(value = 1.2f, type = TextUnitType.Sp),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) { content() }
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    )
}

// ── Row: icon + label + end slot ─────────────────────────────────────────────

@Composable
private fun SettingsRowWithContent(
    icon: ImageVector,
    label: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = ColorTextPrimary)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        content()
    }
}

// ── Row: switch ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = ColorTextPrimary)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = IrisTheme.colors.primary,
            ),
        )
    }
}

// ── Row: tappable ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsTappableRow(
    icon: ImageVector,
    label: String,
    description: String? = null,
    tint: Color = IrisTheme.colors.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = ColorTextPrimary)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                )
            }
        }
        Icon(
            imageVector = PhIcons.Regular.CaretRight,
            contentDescription = null,
            tint = ColorTextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Icon circle ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsIcon(
    icon: ImageVector,
    tint: Color = IrisTheme.colors.primary,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(IrisTheme.colors.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Voice selector ───────────────────────────────────────────────────────────

@Composable
private fun VoiceSelector(
    current : TtsVoice,
    onChange: (TtsVoice) -> Unit,
) {
    val voices = TtsVoice.entries
    val currentIndex = voices.indexOf(current)

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val prev = (currentIndex - 1).coerceAtLeast(0)
                onChange(voices[prev])
            },
            enabled = currentIndex > 0,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = PhIcons.Regular.CaretLeft,
                contentDescription = "Önceki",
                tint = if (currentIndex > 0) IrisTheme.colors.primary
                       else ColorTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = current.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = IrisTheme.colors.primary,
            fontWeight = FontWeight.SemiBold,
        )

        IconButton(
            onClick = {
                val next = (currentIndex + 1).coerceAtMost(voices.lastIndex)
                onChange(voices[next])
            },
            enabled = currentIndex < voices.lastIndex,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = PhIcons.Regular.CaretRight,
                contentDescription = "Sonraki",
                tint = if (currentIndex < voices.lastIndex) IrisTheme.colors.primary
                       else ColorTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Color scheme selector ────────────────────────────────────────────────────

@Composable
private fun ColorSchemeSelector(
    current : ColorSchemeOption,
    onChange: (ColorSchemeOption) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorSchemeOption.entries.forEach { scheme ->
            val irisColors = scheme.toIrisColorScheme()
            val selected = scheme == current
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(irisColors.primary)
                    .clickable { onChange(scheme) }
                    .then(
                        if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                        else Modifier
                    ),
            )
        }
    }
}

// ── Provider selector ────────────────────────────────────────────────────────

@Composable
private fun ProviderSelector(
    current : String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(IrisTheme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PhIcons.Regular.Cpu,
                contentDescription = null,
                tint = IrisTheme.colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Constants.LLM_PROVIDERS.forEach { provider ->
                val selected = provider == current
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) IrisTheme.colors.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onChange(provider) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = Constants.providerDisplayName(provider),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.background
                                else ColorTextPrimary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── TTS provider selector ────────────────────────────────────────────────────

@Composable
private fun TtsProviderSelector(
    current : TtsProviderType,
    onChange: (TtsProviderType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TtsProviderType.entries.forEach { provider ->
            val selected = provider == current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) IrisTheme.colors.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onChange(provider) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.background
                            else ColorTextPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── Autonomy level selector ──────────────────────────────────────────────────

@Composable
private fun AutonomyLevelSelector(
    current : AutonomyLevel,
    onChange: (AutonomyLevel) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AutonomyLevel.entries.forEach { level ->
            val selected = level == current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) IrisTheme.colors.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onChange(level) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = level.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.background
                            else ColorTextPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── Model selector ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    current : String,
    provider: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val models = Constants.modelsForProvider(provider)
    val selectedModel = models.find { it.apiName == current }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(46.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                )
                Text(
                    text = selectedModel?.displayName ?: current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ColorTextPrimary,
                )
            }
            Icon(
                imageVector = PhIcons.Regular.CaretDown,
                contentDescription = null,
                tint = ColorTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                ),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                models.forEachIndexed { index, model ->
                    val isSelected = model.apiName == current
                    Surface(
                        onClick = {
                            onChange(model.apiName)
                            expanded = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) IrisTheme.colors.primary
                                            else ColorTextPrimary,
                                )
                                Text(
                                    text = model.apiName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorTextSecondary,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = PhIcons.Regular.Check,
                                    contentDescription = null,
                                    tint = IrisTheme.colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    if (index < models.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        )
                    }
                }
            }
        }
    }
}