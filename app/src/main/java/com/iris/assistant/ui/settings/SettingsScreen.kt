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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack            : () -> Unit,
    onOpenLocalModels : () -> Unit = {},
    viewModel        : SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }
    var permRefreshTrigger by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefreshTrigger++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

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

    if (showAccessibilityGuide) {
        AccessibilityGuideDialog(onDismiss = { showAccessibilityGuide = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
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

            // ── İzinler ─────────────────────────────────────────────────────────
            SettingsGroup(title = "İzinler") {
                PermissionSection(
                    context = context,
                    refreshTrigger = permRefreshTrigger,
                    onOpenAccessibilityGuide = { showAccessibilityGuide = true },
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

// ── Accessibility guide dialog ──────────────────────────────────────────────

@Composable
private fun AccessibilityGuideDialog(onDismiss: () -> Unit) {
    val primary = IrisTheme.colors.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Erişilebilirlik Servisi Aktivasyonu",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = "IRIS'in ekranı okuması ve sizin adınıza tıklaması için erişilebilirlik servisini etkinleştirmeniz gerekiyor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                )
                Spacer(Modifier.height(16.dp))

                listOf(
                    "1️⃣" to "Ayarlar uygulamasını açın",
                    "2️⃣" to "Erişilebilirlik > Yüklü uygulamalar bölümüne girin",
                    "3️⃣" to "IRIS'i bulun ve üzerine dokunun",
                    "4️⃣" to "Erişilebilirlik servisi anahtarını açın",
                    "5️⃣" to "Açılan uyarıda İzin Ver'e dokunun",
                ).forEach { (emoji, step) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorTextPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tamam", color = primary)
            }
        },
    )
}

// ── Permission section ──────────────────────────────────────────────────────

private class PermissionCheck(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent,
    val hasGuide: Boolean = false,
)

@Composable
private fun PermissionSection(
    context: Context,
    refreshTrigger: Int,
    onOpenAccessibilityGuide: () -> Unit,
) {
    val primary = IrisTheme.colors.primary

    val permissionGroups = remember(refreshTrigger) {
        listOf(
            listOf(
                PermissionCheck(
                    label = "Mikrofon",
                    icon = PhIcons.Regular.Microphone,
                    description = "Sesli komut ve konuşma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Bildirimler",
                    icon = PhIcons.Regular.Bell,
                    description = "Bildirim gönderme",
                    isGranted = { ctx -> androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled() },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Pil optimizasyonu",
                    icon = PhIcons.Regular.BatteryHigh,
                    description = "Arka planda çalışma",
                    isGranted = { ctx ->
                        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        pm.isIgnoringBatteryOptimizations(ctx.packageName)
                    },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Erişilebilirlik Servisi",
                    icon = PhIcons.Regular.Eye,
                    description = "Ekran okuma ve kontrol",
                    isGranted = { ctx ->
                        val service = "${ctx.packageName}/com.iris.assistant.service.accessibility.IrisAccessibilityService"
                        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                        enabled.split(':').any { it.trim().startsWith(ctx.packageName) }
                    },
                    settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    hasGuide = true,
                ),
                PermissionCheck(
                    label = "Üstte görünme",
                    icon = PhIcons.Regular.Stack,
                    description = "Floating baloncuk ve önizleme",
                    isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Arama",
                    icon = PhIcons.Regular.PhoneCall,
                    description = "Telefon araması yapma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "SMS",
                    icon = PhIcons.Regular.ChatDots,
                    description = "SMS gönderme",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Kişiler",
                    icon = PhIcons.Regular.AddressBook,
                    description = "Kişi listesini okuma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Telefon durumu",
                    icon = PhIcons.Regular.DeviceMobile,
                    description = "Cihaz bilgisi okuma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Ses ayarları",
                    icon = PhIcons.Regular.SpeakerHigh,
                    description = "Ses seviyesi değiştirme",
                    isGranted = { true },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "WiFi",
                    icon = PhIcons.Regular.WifiHigh,
                    description = "WiFi açma/kapama",
                    isGranted = { true },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Bluetooth",
                    icon = PhIcons.Regular.Bluetooth,
                    description = "Bluetooth açma/kapama",
                    isGranted = { true },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Kamera",
                    icon = PhIcons.Regular.Camera,
                    description = "Flaş kontrolü",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Sistem ayarları",
                    icon = PhIcons.Regular.GearSix,
                    description = "Parlaklık değiştirme",
                    isGranted = { ctx -> Settings.System.canWrite(ctx) },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Kesin alarm",
                    icon = PhIcons.Regular.Alarm,
                    description = "Hatırlatıcı kurma",
                    isGranted = { ctx ->
                        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                        alarmManager.canScheduleExactAlarms()
                    },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Takvim",
                    icon = PhIcons.Regular.Calendar,
                    description = "Takvim okuma/yazma",
                    isGranted = { ctx ->
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
        )
    }

    Column {
        permissionGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) PermissionGroupDivider()

            group.forEachIndexed { permIndex, perm ->
                if (permIndex > 0) PermissionItemDivider()

                val granted = perm.isGranted(context)
                PermissionItemRow(
                    icon = perm.icon,
                    label = perm.label,
                    description = perm.description,
                    granted = granted,
                    hasGuide = perm.hasGuide,
                    onOpenSettings = {
                        context.startActivity(perm.settingsIntent(context))
                    },
                    onOpenGuide = if (perm.hasGuide) onOpenAccessibilityGuide else null,
                )
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    hasGuide: Boolean,
    onOpenSettings: () -> Unit,
    onOpenGuide: (() -> Unit)?,
) {
    val primary = IrisTheme.colors.primary
    val green = Color(0xFF34C759)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PermissionIcon(icon, granted, primary)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ColorTextPrimary,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (granted) green else gray)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }

        if (hasGuide && onOpenGuide != null) {
            Surface(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(10.dp),
                color = primary.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PhIcons.Regular.BookOpen,
                        contentDescription = "Rehber",
                        tint = primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(10.dp),
            color = primary.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PhIcons.Regular.ArrowSquareOut,
                    contentDescription = "Ayarlara git",
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionIcon(icon: ImageVector, granted: Boolean, tint: Color) {
    val alpha = if (granted) 0.12f else 0.06f
    val iconAlpha = if (granted) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = iconAlpha),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PermissionGroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    )
}

@Composable
private fun PermissionItemDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    )
}