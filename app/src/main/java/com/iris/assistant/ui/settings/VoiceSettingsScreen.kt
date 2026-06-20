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
import androidx.compose.foundation.lazy.items
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
import com.phosphor.icons.regular.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onBack  : () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                        text = "Ses Ayarları",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── Provider section ─────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("TTS Sağlayıcısı")
                TtsProviderCards(
                    current  = uiState.ttsProvider,
                    onChange = viewModel::onTtsProviderChange,
                )
            }

            // ── Voice section ────────────────────────────────────────
            item {
                SectionLabel("Ses Karakteri")
            }

            items(TtsVoice.entries) { voice ->
                VoiceCard(
                    voice    = voice,
                    selected = voice == uiState.ttsVoice,
                    onClick  = { viewModel.onTtsVoiceChange(voice) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = IrisTheme.colors.primary,
        letterSpacing = TextUnit(value = 1.2f, type = TextUnitType.Sp),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun TtsProviderCards(
    current : TtsProviderType,
    onChange: (TtsProviderType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TtsProviderType.entries.forEach { provider ->
            val selected = provider == current
            val bgColor by animateColorAsState(
                targetValue = if (selected) IrisTheme.colors.primary
                              else MaterialTheme.colorScheme.surface,
                animationSpec = tween(200),
                label = "providerBg",
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) IrisTheme.colors.primary
                              else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                animationSpec = tween(200),
                label = "providerBorder",
            )
            val providerIcon = when (provider) {
                TtsProviderType.GEMINI  -> PhIcons.Regular.Sparkle
                TtsProviderType.MMS     -> PhIcons.Regular.Cloud
                TtsProviderType.ANDROID -> PhIcons.Regular.DeviceMobile
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .border(
                        width = if (selected) 0.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onChange(provider) }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White.copy(alpha = 0.2f)
                            else IrisTheme.colors.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = providerIcon,
                        contentDescription = null,
                        tint = if (selected) Color.White else IrisTheme.colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) Color.White else ColorTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White.copy(alpha = 0.7f)
                            else ColorTextSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice   : TtsVoice,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) IrisTheme.colors.primary.copy(alpha = 0.08f)
                      else Color.Transparent,
        animationSpec = tween(200),
        label = "voiceBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) IrisTheme.colors.primary.copy(alpha = 0.3f)
                      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        animationSpec = tween(200),
        label = "voiceBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle with first letter
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) IrisTheme.colors.primary
                    else IrisTheme.colors.primary.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = voice.displayName.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else IrisTheme.colors.primary,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = voice.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = ColorTextPrimary,
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = PhIcons.Regular.CheckCircle,
                        contentDescription = null,
                        tint = IrisTheme.colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = voice.description,
                style = MaterialTheme.typography.bodyMedium,
                color = ColorTextSecondary,
            )
        }

        Icon(
            imageVector = PhIcons.Regular.Play,
            contentDescription = "Önizle",
            tint = if (selected) IrisTheme.colors.primary else ColorTextSecondary.copy(alpha = 0.5f),
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (selected) IrisTheme.colors.primary.copy(alpha = 0.1f)
                    else Color.Transparent
                )
                .padding(6.dp),
        )
    }
}