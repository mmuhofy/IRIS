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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.Cloud
import com.phosphor.icons.regular.DeviceMobile
import com.phosphor.icons.regular.Play
import com.phosphor.icons.regular.Sparkle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onBack    : () -> Unit,
    viewModel : VoiceSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // NOTE: containerColor = Color.Transparent — do NOT change to opaque.
    // Opaque background breaks nav transition animations in IrisNavGraph.kt.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Ses Ayarları",
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
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {

            // ── Provider section ──────────────────────────────────────────
            item {
                VoiceSectionLabel("TTS Sağlayıcısı")
                TtsProviderCards(
                    current  = uiState.ttsProvider,
                    onChange = viewModel::onTtsProviderChange,
                )
                Spacer(Modifier.height(24.dp))
            }

            // ── Voice character section — compact rows in a single card ───
            item {
                VoiceSectionLabel("Ses Karakteri")
                VoiceListCard(
                    voices   = TtsVoice.entries,
                    selected = uiState.ttsVoice,
                    onSelect = viewModel::onTtsVoiceChange,
                )
            }
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun VoiceSectionLabel(text: String) {
    Text(
        text          = text.uppercase(),
        style         = MaterialTheme.typography.labelSmall,
        color         = IrisTheme.colors.primary,
        letterSpacing = TextUnit(1.2f, TextUnitType.Sp),
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

// ── TTS provider cards ────────────────────────────────────────────────────────

@Composable
private fun TtsProviderCards(
    current : TtsProviderType,
    onChange: (TtsProviderType) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TtsProviderType.entries.forEach { provider ->
            val selected = provider == current
            val bgColor  = if (selected) IrisTheme.colors.primary
                           else MaterialTheme.colorScheme.surface
            val iconTint = if (selected) Color.White else IrisTheme.colors.primary
            val textColor = if (selected) Color.White else ColorTextPrimary
            val subColor  = if (selected) Color.White.copy(alpha = 0.7f) else ColorTextSecondary

            val providerIcon = when (provider) {
                TtsProviderType.GEMINI  -> PhIcons.Regular.Sparkle
                TtsProviderType.MMS     -> PhIcons.Regular.Cloud
                TtsProviderType.ANDROID -> PhIcons.Regular.DeviceMobile
            }

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
                        imageVector        = providerIcon,
                        contentDescription = null,
                        tint               = iconTint,
                        modifier           = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = provider.displayName,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color      = textColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = provider.description,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = subColor,
                    maxLines = 2,
                )
            }
        }
    }
}

// ── Voice list — all voices in a single card, compact rows ───────────────────

@Composable
private fun VoiceListCard(
    voices  : List<TtsVoice>,
    selected: TtsVoice,
    onSelect: (TtsVoice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        voices.forEachIndexed { index, voice ->
            VoiceRow(
                voice    = voice,
                selected = voice == selected,
                onClick  = { onSelect(voice) },
            )
            if (index < voices.lastIndex) {
                // Divider aligned to content (left of avatar = 16dp padding + 40dp avatar = 56dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp)
                        .height(0.5.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                        ),
                )
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice   : TtsVoice,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val rowBg   = if (selected) IrisTheme.colors.primary.copy(alpha = 0.07f) else Color.Transparent
    val nameTxt = if (selected) IrisTheme.colors.primary else ColorTextPrimary

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar — visible only when selected
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) IrisTheme.colors.primary else Color.Transparent
                )
        )
        Spacer(Modifier.width(10.dp))

        // Avatar circle with initial
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (selected) IrisTheme.colors.primary
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = voice.displayName.take(1),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = if (selected) Color.White else IrisTheme.colors.primary,
            )
        }
        Spacer(Modifier.width(12.dp))

        // Name + personality tag
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = voice.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color      = nameTxt,
            )
            // Personality tag pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text  = voice.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) IrisTheme.colors.primary else ColorTextSecondary,
                )
            }
        }

        // Play preview button
        Box(
            modifier         = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = PhIcons.Regular.Play,
                contentDescription = "Önizle",
                tint               = if (selected) IrisTheme.colors.primary
                                     else ColorTextSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}