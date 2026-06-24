package com.iris.assistant.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.CaretDown
import com.phosphor.icons.regular.Check
import com.phosphor.icons.regular.Cloud
import com.phosphor.icons.regular.Cpu
import com.phosphor.icons.regular.DeviceMobile
import com.phosphor.icons.regular.Download
import com.phosphor.icons.regular.Sparkle

// Maps provider key → icon
private fun providerIcon(provider: String): ImageVector = when (provider) {
    Constants.LLM_PROVIDER_GEMINI -> PhIcons.Regular.Sparkle
    Constants.LLM_PROVIDER_GROQ   -> PhIcons.Regular.Cloud
    Constants.LLM_PROVIDER_LOCAL  -> PhIcons.Regular.Cpu
    else                           -> PhIcons.Regular.DeviceMobile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    onBack            : () -> Unit,
    onOpenLocalModels : () -> Unit = {},
    viewModel         : ModelSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Model",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding      = PaddingValues(bottom = 32.dp),
        ) {

            // ── Provider cards ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                ModelSectionLabel("Sağlayıcı")
                ProviderCards(
                    current  = uiState.llmProvider,
                    onChange = viewModel::onLlmProviderChange,
                )
            }

            // ── Model selector ────────────────────────────────────────────
            if (uiState.llmProvider != Constants.LLM_PROVIDER_LOCAL) {
                item {
                    ModelSectionLabel("Model")
                    ModelSelector(
                        current  = uiState.llmModel,
                        provider = uiState.llmProvider,
                        onChange = viewModel::onLlmModelChange,
                    )
                }
            } else {
                item {
                    ModelSectionLabel("Yerel Model")
                    ModelInfoRow(
                        icon        = PhIcons.Regular.Download,
                        label       = "Seçili model",
                        description = uiState.localModelName.ifBlank { "Henüz seçilmedi" },
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(IrisTheme.colors.primary.copy(alpha = 0.10f))
                            .border(
                                width = 1.dp,
                                color = IrisTheme.colors.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable(onClick = onOpenLocalModels)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = "Yerel Modelleri Yönet",
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = IrisTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

@Composable
private fun ModelSectionLabel(text: String) {
    Text(
        text          = text.uppercase(),
        style         = MaterialTheme.typography.labelSmall,
        color         = IrisTheme.colors.primary,
        letterSpacing = 1.2.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

// ---------------------------------------------------------------------------
// Provider cards — full card per provider (same pattern as VoiceSettingsScreen)
// ---------------------------------------------------------------------------

@Composable
private fun ProviderCards(
    current : String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Constants.LLM_PROVIDERS.forEach { provider ->
            val selected = provider == current

            val bgColor by animateColorAsState(
                targetValue   = if (selected) IrisTheme.colors.primary
                                else MaterialTheme.colorScheme.surface,
                animationSpec = tween(200),
                label         = "providerBg_$provider",
            )
            val borderColor by animateColorAsState(
                targetValue   = if (selected) IrisTheme.colors.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                animationSpec = tween(200),
                label         = "providerBorder_$provider",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .border(
                        width = if (selected) 0.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable { onChange(provider) }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White.copy(alpha = 0.2f)
                            else IrisTheme.colors.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = providerIcon(provider),
                        contentDescription = null,
                        tint               = if (selected) Color.White else IrisTheme.colors.primary,
                        modifier           = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = Constants.providerDisplayName(provider),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color      = if (selected) Color.White else ColorTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = providerSubtitle(provider),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (selected) Color.White.copy(alpha = 0.7f) else ColorTextSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun providerSubtitle(provider: String): String = when (provider) {
    Constants.LLM_PROVIDER_GEMINI -> "Flash, Pro, Ultra"
    Constants.LLM_PROVIDER_GROQ   -> "Llama, Mixtral"
    Constants.LLM_PROVIDER_LOCAL  -> "On-device"
    else                           -> ""
}

// ---------------------------------------------------------------------------
// Model dropdown selector
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    current : String,
    provider: String,
    onChange: (String) -> Unit,
) {
    var expanded   by remember { mutableStateOf(false) }
    val models      = Constants.modelsForProvider(provider)
    val selectedModel = models.find { it.apiName == current }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(IrisTheme.colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = PhIcons.Regular.Cpu,
                        contentDescription = null,
                        tint               = IrisTheme.colors.primary,
                        modifier           = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Seçili model",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextSecondary,
                    )
                    Text(
                        text       = selectedModel?.displayName ?: current,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color      = ColorTextPrimary,
                    )
                }
                Icon(
                    imageVector        = PhIcons.Regular.CaretDown,
                    contentDescription = null,
                    tint               = ColorTextSecondary,
                    modifier           = Modifier.size(16.dp),
                )
            }

            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false },
                shape            = RoundedCornerShape(18.dp),
                modifier         = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                    ),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    models.forEachIndexed { index, model ->
                        val isSelected = model.apiName == current
                        Surface(
                            onClick  = { onChange(model.apiName); expanded = false },
                            shape    = RoundedCornerShape(12.dp),
                            color    = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text  = model.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) IrisTheme.colors.primary
                                                else ColorTextPrimary,
                                    )
                                    Text(
                                        text  = model.apiName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ColorTextSecondary,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector        = PhIcons.Regular.Check,
                                        contentDescription = null,
                                        tint               = IrisTheme.colors.primary,
                                        modifier           = Modifier.size(18.dp),
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
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Info row (local model display — read only)
// ---------------------------------------------------------------------------

@Composable
private fun ModelInfoRow(
    icon        : ImageVector,
    label       : String,
    description : String,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(IrisTheme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = IrisTheme.colors.primary,
                modifier           = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
            Text(
                text       = description,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = IrisTheme.colors.primary,
            )
        }
    }
}