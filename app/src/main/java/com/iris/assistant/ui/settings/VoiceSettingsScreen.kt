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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

            // ── Voice character — swipeable carousel ──────────────────────
            item {
                VoiceSectionLabel("Ses Karakteri")
                VoiceCarousel(
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

// ── Voice character carousel ──────────────────────────────────────────────────

@Composable
private fun VoiceCarousel(
    voices  : List<TtsVoice>,
    selected: TtsVoice,
    onSelect: (TtsVoice) -> Unit,
) {
    val initialIndex = voices.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount   = { voices.size },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        val voice = voices.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (voice != selected) onSelect(voice)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state       = pagerState,
            beyondViewportPageCount = 1,
            modifier    = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) { page ->
            VoicePreview(voice = voices[page])
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            voices.forEachIndexed { index, _ ->
                val isSelected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 16.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) IrisTheme.colors.primary
                            else Color.White.copy(alpha = 0.15f)
                        )
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                                onSelect(voices[index])
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun VoicePreview(voice: TtsVoice) {
    val initial = voice.displayName.take(1)

    Column(
        modifier          = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large avatar circle
        Box(
            modifier         = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(IrisTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = initial,
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text       = voice.displayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color      = ColorTextPrimary,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        // Personality tag pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IrisTheme.colors.primary.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text  = voice.description,
                style = MaterialTheme.typography.bodyMedium,
                color = IrisTheme.colors.primary,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Play preview button
        Box(
            modifier         = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(IrisTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = PhIcons.Regular.Play,
                contentDescription = "Önizle",
                tint               = Color.White,
                modifier           = Modifier.size(24.dp),
            )
        }
    }
}
