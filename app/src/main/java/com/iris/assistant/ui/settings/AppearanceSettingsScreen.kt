package com.iris.assistant.ui.settings

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.CheckCircle

// ---------------------------------------------------------------------------
// Per-theme display metadata
// ---------------------------------------------------------------------------
private data class ThemeMeta(
    val option      : ColorSchemeOption,
    val label       : String,
    val description : String,
)

private val ALL_THEMES = listOf(
    ThemeMeta(ColorSchemeOption.LAVENDER,   "Lavender",   "Mor & indigo"),
    ThemeMeta(ColorSchemeOption.SUNSET,     "Sunset",     "Kırmızı & altın"),
    ThemeMeta(ColorSchemeOption.OCEAN,      "Ocean",      "Siyan & mavi"),
    ThemeMeta(ColorSchemeOption.FOREST,     "Forest",     "Yeşil & sarı"),
    ThemeMeta(ColorSchemeOption.ROSE,       "Rose",       "Pembe & mor"),
    ThemeMeta(ColorSchemeOption.MONOCHROME, "Monochrome", "Gri tonları"),
    ThemeMeta(ColorSchemeOption.NEURAL,     "Neural",     "Buz mavisi & fuşya"),
    ThemeMeta(ColorSchemeOption.AURORA,     "Aurora",     "Kuzey ışıkları"),
    ThemeMeta(ColorSchemeOption.MONOLITH,   "Monolith",   "Premium minimal"),
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Görünüm",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = PhIcons.Regular.ArrowLeft,
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
            modifier        = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding  = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text          = "RENK TEMASI",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = IrisTheme.colors.primary,
                    letterSpacing = 1.2.sp,
                    modifier      = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            items(
                count = ALL_THEMES.size,
                key   = { ALL_THEMES[it].option.name },
            ) { index ->
                val meta     = ALL_THEMES[index]
                val selected = uiState.colorScheme == meta.option
                ThemeCard(
                    meta     = meta,
                    selected = selected,
                    onClick  = { viewModel.onColorSchemeChange(meta.option) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Theme card
// ---------------------------------------------------------------------------
@Composable
private fun ThemeCard(
    meta    : ThemeMeta,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val colors = meta.option.toIrisColorScheme()

    val borderColor = if (selected) colors.primary else Color.White.copy(alpha = 0.07f)
    val borderWidth = if (selected) 1.5.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gradient preview swatch
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(colors.primary, colors.gradientEnd, colors.secondary)
                    )
                )
        )

        Spacer(Modifier.width(14.dp))

        // Labels
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = meta.label,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (selected) colors.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = meta.description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }

        // Selected checkmark
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector        = PhIcons.Fill.CheckCircle,
                contentDescription = null,
                tint               = colors.primary,
                modifier           = Modifier.size(22.dp),
            )
        }
    }
}