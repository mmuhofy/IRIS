// app/src/main/java/com/iris/assistant/ui/settings/AppearanceSettingsScreen.kt
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.AppFont
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft

// ---------------------------------------------------------------------------
// Per-theme display metadata
// ---------------------------------------------------------------------------

private data class ThemeMeta(
    val option      : ColorSchemeOption,
    val label       : String,
    val description : String,
)

private val ALL_THEMES = listOf(
    ThemeMeta(ColorSchemeOption.SLATE,       "Stone",       "Sıcak taş"),
    ThemeMeta(ColorSchemeOption.ROSE_QUARTZ, "Rose Quartz", "Toz pembe"),
    ThemeMeta(ColorSchemeOption.SAGE,        "Sage",        "Adaçayı yeşili"),
    ThemeMeta(ColorSchemeOption.COBALT,      "Cobalt",      "Gece mavisi — varsayılan"),
    ThemeMeta(ColorSchemeOption.EMBER,       "Ember",       "Bakır sıcaklığı"),
    ThemeMeta(ColorSchemeOption.MONOCHROME,  "Monochrome",  "Grafit gri"),
)

private val FONT_OPTIONS: List<AppFont> = AppFont.builtin

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack    : () -> Unit,
    viewModel : AppearanceSettingsViewModel = hiltViewModel(),
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
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding      = PaddingValues(bottom = 32.dp),
        ) {

            // ── Appearance mode ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                AppearanceSectionLabel("Görünüm Modu")
            }
            item {
                DarkModeToggle(
                    enabled  = uiState.isDarkMode,
                    onToggle = { viewModel.onDarkModeChange(it) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }

            // ── Color scheme — swipeable carousel ────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                AppearanceSectionLabel("Renk Teması")
            }
            item {
                ThemeCarousel(
                    themes   = ALL_THEMES,
                    selected = uiState.colorScheme,
                    onSelect = { viewModel.onColorSchemeChange(it) },
                )
            }

            // ── Font ──────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                AppearanceSectionLabel("Yazı Tipi")
            }

            items(
                count = FONT_OPTIONS.size,
                key   = { FONT_OPTIONS[it].key },
            ) { index ->
                val font     = FONT_OPTIONS[index]
                val selected = uiState.fontFamily.key == font.key
                FontCard(
                    font     = font,
                    selected = selected,
                    onClick  = { viewModel.onFontChange(font) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Theme carousel — swipeable pager
// ---------------------------------------------------------------------------

@Composable
private fun ThemeCarousel(
    themes  : List<ThemeMeta>,
    selected: ColorSchemeOption,
    onSelect: (ColorSchemeOption) -> Unit,
) {
    val initialIndex = themes.indexOfFirst { it.option == selected }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount   = { themes.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        val theme = themes.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (theme.option != selected) onSelect(theme.option)
    }

    LaunchedEffect(selected) {
        val targetIndex = themes.indexOfFirst { it.option == selected }.coerceAtLeast(0)
        if (pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state       = pagerState,
            beyondViewportPageCount = 1,
            modifier    = Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) { page ->
            val meta = themes[page]
            ThemePreview(meta = meta)
        }

        Spacer(Modifier.height(12.dp))

        // Page indicator dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themes.forEachIndexed { index, meta ->
                val isSelected = index == pagerState.currentPage
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) meta.option.seedPrimary
                                  else Color.White.copy(alpha = 0.15f),
                    label       = "dot$index",
                )
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 16.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(dotColor)
                        .clickable {
                            // animate to the clicked page
                        },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Theme preview — large gradient circle + label + description
// ---------------------------------------------------------------------------

@Composable
private fun ThemePreview(meta: ThemeMeta) {
    val seedColors = with(meta.option) { listOf(seedPrimary, seedGradient, seedSecondary) }

    Column(
        modifier          = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large gradient circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(colors = seedColors)),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text       = meta.label,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color      = ColorTextPrimary,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text       = meta.description,
            style      = MaterialTheme.typography.bodyMedium,
            color      = ColorTextSecondary,
            textAlign  = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

@Composable
private fun AppearanceSectionLabel(text: String) {
    Text(
        text          = text.uppercase(),
        style         = MaterialTheme.typography.labelSmall,
        color         = IrisTheme.colors.primary,
        letterSpacing = 1.2.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Dark/Light mode toggle
// ---------------------------------------------------------------------------

@Composable
private fun DarkModeToggle(
    enabled : Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = if (enabled) "Karanlık Mod" else "Aydınlık Mod",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = ColorTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = "Arayüz renklerini gece/gündüz moduna göre ayarla",
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked         = enabled,
            onCheckedChange = onToggle,
        )
    }
}

// ---------------------------------------------------------------------------
// Font card — renders "Aa" preview in the font being selected
// ---------------------------------------------------------------------------

@Composable
private fun FontCard(
    font    : AppFont,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue   = if (selected) IrisTheme.colors.primary else Color.White.copy(alpha = 0.07f),
        animationSpec = tween(200),
        label         = "fontBorder",
    )
    val borderWidth = if (selected) 1.5.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "Aa",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) IrisTheme.colors.primary else ColorTextPrimary,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = font.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) IrisTheme.colors.primary else ColorTextPrimary,
            )
            Text(
                text  = "Merhaba, ben IRIS!",
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }

        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(IrisTheme.colors.primary),
            )
        }
    }
}
