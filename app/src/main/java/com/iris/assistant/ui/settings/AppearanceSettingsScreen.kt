// app/src/main/java/com/iris/assistant/ui/settings/AppearanceSettingsScreen.kt
package com.iris.assistant.ui.settings

import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    ThemeMeta(ColorSchemeOption.SLATE,       "Slate",       "Off-white & amber — varsayılan"),
    ThemeMeta(ColorSchemeOption.ROSE_QUARTZ, "Rose Quartz", "Soft pembe"),
    ThemeMeta(ColorSchemeOption.SAGE,        "Sage",        "Soft yeşil"),
    ThemeMeta(ColorSchemeOption.COBALT,      "Cobalt",      "Pastel mavi"),
    ThemeMeta(ColorSchemeOption.EMBER,       "Ember",       "Turuncu & altın"),
    ThemeMeta(ColorSchemeOption.MONOCHROME,  "Monochrome",  "Sadece gri & beyaz"),
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

            // ── Material You (Android 12+ only) ──────────────────────────
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Spacer(Modifier.height(4.dp))
                    AppearanceSectionLabel("Sistem Entegrasyonu")
                }
                item {
                    MaterialYouToggle(
                        enabled  = uiState.useMaterialYou,
                        onToggle = { viewModel.onMaterialYouChange(it) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ── Color scheme ──────────────────────────────────────────────
            item {
                AppearanceSectionLabel("Renk Teması")
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
// Material You toggle — only rendered on Android 12+, caller guards too
// ---------------------------------------------------------------------------

@Composable
private fun MaterialYouToggle(
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
                text       = "Material You",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = ColorTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = "Arka plan renkleri duvar kağıdından gelir, accent rengi sabit kalır",
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
// Theme card
// ---------------------------------------------------------------------------

@Composable
private fun ThemeCard(
    meta    : ThemeMeta,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val colors      = meta.option.toIrisColorScheme()
    val borderColor by animateColorAsState(
        targetValue   = if (selected) colors.primary else Color.White.copy(alpha = 0.07f),
        animationSpec = tween(200),
        label         = "themeBorder",
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = meta.label,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (selected) colors.primary else ColorTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = meta.description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }

        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector        = PhIcons.Regular.CheckCircle,
                contentDescription = null,
                tint               = colors.primary,
                modifier           = Modifier.size(22.dp),
            )
        }
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
                    if (selected) IrisTheme.colors.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "Aa",
                style = TextStyle(
                    fontFamily = font.fontFamily,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (selected) IrisTheme.colors.primary else ColorTextPrimary,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = font.displayName,
                style = TextStyle(
                    fontFamily = font.fontFamily,
                    fontSize   = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) IrisTheme.colors.primary else ColorTextPrimary,
            )
            Text(
                text  = "Merhaba, ben IRIS!",
                style = TextStyle(
                    fontFamily = font.fontFamily,
                    fontSize   = 12.sp,
                ),
                color = ColorTextSecondary,
            )
        }

        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector        = PhIcons.Regular.CheckCircle,
                contentDescription = null,
                tint               = IrisTheme.colors.primary,
                modifier           = Modifier.size(22.dp),
            )
        }
    }
}