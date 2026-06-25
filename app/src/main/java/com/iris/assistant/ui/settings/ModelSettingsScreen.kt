package com.iris.assistant.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
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
import com.phosphor.icons.regular.CheckCircle
import com.phosphor.icons.regular.Cloud
import com.phosphor.icons.regular.Cpu
import com.phosphor.icons.regular.DeviceMobile
import com.phosphor.icons.regular.Download
import com.phosphor.icons.regular.Sparkle

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
            contentPadding      = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {

            // ── Provider section ──────────────────────────────────────────
            item {
                ModelSectionLabel("Sağlayıcı")
                ProviderCards(
                    current  = uiState.llmProvider,
                    onChange = viewModel::onLlmProviderChange,
                )
            }

            // ── Model section ─────────────────────────────────────────────
            if (uiState.llmProvider != Constants.LLM_PROVIDER_LOCAL) {
                item {
                    ModelSectionLabel("Model")
                    // Inline model rows — no dropdown needed
                    InlineModelSelector(
                        current  = uiState.llmModel,
                        provider = uiState.llmProvider,
                        onChange = viewModel::onLlmModelChange,
                    )
                }
            } else {
                item {
                    ModelSectionLabel("Yerel Model")
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Selected model info row
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
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector        = PhIcons.Regular.Download,
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
                                    text       = uiState.localModelName.ifBlank { "Seçilmedi" },
                                    style      = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color      = if (uiState.localModelName.isNotBlank())
                                                     IrisTheme.colors.primary
                                                 else ColorTextSecondary,
                                )
                            }
                        }
                        // Navigate to local models
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable(onClick = onOpenLocalModels)
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text       = "Yerel Modelleri Yönet →",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = IrisTheme.colors.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

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

// ── Provider cards ────────────────────────────────────────────────────────────

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
            val selected  = provider == current
            val bgColor   = if (selected) IrisTheme.colors.primary else MaterialTheme.colorScheme.surface
            val iconTint  = if (selected) Color.White else IrisTheme.colors.primary
            val textColor = if (selected) Color.White else ColorTextPrimary
            val subColor  = if (selected) Color.White.copy(alpha = 0.7f) else ColorTextSecondary

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
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
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = providerIcon(provider),
                        contentDescription = null,
                        tint               = iconTint,
                        modifier           = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = Constants.providerDisplayName(provider),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color      = textColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = providerSubtitle(provider),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = subColor,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun providerSubtitle(provider: String): String = when (provider) {
    Constants.LLM_PROVIDER_GEMINI -> "Flash · Pro · Ultra"
    Constants.LLM_PROVIDER_GROQ   -> "Llama · Mixtral"
    Constants.LLM_PROVIDER_LOCAL  -> "On-device"
    else                           -> ""
}

// ── Inline model selector — card with rows, no dropdown ──────────────────────

@Composable
private fun InlineModelSelector(
    current : String,
    provider: String,
    onChange: (String) -> Unit,
) {
    val models = Constants.modelsForProvider(provider)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        models.forEachIndexed { index, model ->
            val selected = model.apiName == current

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) IrisTheme.colors.primary.copy(alpha = 0.07f)
                        else Color.Transparent
                    )
                    .clickable { onChange(model.apiName) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (selected) IrisTheme.colors.primary else Color.Transparent
                        )
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = model.displayName,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (selected) IrisTheme.colors.primary else ColorTextPrimary,
                    )
                    // API name in monospace-like style
                    Text(
                        text  = model.apiName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily    = FontFamily.Monospace,
                            letterSpacing = 0.2.sp,
                        ),
                        color = ColorTextSecondary,
                    )
                }

                if (selected) {
                    Icon(
                        imageVector        = PhIcons.Regular.CheckCircle,
                        contentDescription = null,
                        tint               = IrisTheme.colors.primary,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }

            if (index < models.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 31.dp) // accent bar 3dp + spacer 12dp + 16dp padding
                        .height(0.5.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                )
            }
        }
    }
}