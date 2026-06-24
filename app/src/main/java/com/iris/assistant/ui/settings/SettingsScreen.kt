package com.iris.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.CaretRight
import com.phosphor.icons.regular.Cpu
import com.phosphor.icons.regular.Gear
import com.phosphor.icons.regular.Headphones
import com.phosphor.icons.regular.Palette
import com.phosphor.icons.regular.Shield
import com.phosphor.icons.regular.Sparkle
import com.phosphor.icons.regular.Terminal
import com.phosphor.icons.regular.Trash
import com.phosphor.icons.regular.Waveform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack           : () -> Unit,
    onOpenModel      : () -> Unit = {},
    onOpenAppearance : () -> Unit = {},
    onOpenBackground : () -> Unit = {},
    onOpenAutonomy   : () -> Unit = {},
    onOpenSystem     : () -> Unit = {},
    onOpenVoice      : () -> Unit = {},
    onOpenData       : () -> Unit = {},
    onOpenPowerMode  : () -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Ayarlar",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick  = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(24.dp),
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
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        ) {

            // ── Hero header ───────────────────────────────────────────────
            item {
                IrisSettingsHeader()
                Spacer(Modifier.height(28.dp))
            }

            // ── Group 1: Asistan ──────────────────────────────────────────
            item {
                SettingsSectionLabel("Asistan")
                SettingsCategoryCard {
                    SettingsRow(
                        icon        = PhIcons.Regular.Waveform,
                        label       = "Ses",
                        description = "Ses karakteri ve sağlayıcı",
                        onClick     = onOpenVoice,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon        = PhIcons.Regular.Cpu,
                        label       = "Model",
                        description = "AI sağlayıcı ve model seçimi",
                        onClick     = onOpenModel,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Group 2: Görünüm ──────────────────────────────────────────
            item {
                SettingsSectionLabel("Görünüm")
                SettingsCategoryCard {
                    SettingsRow(
                        icon        = PhIcons.Regular.Palette,
                        label       = "Görünüm",
                        description = "Renk teması ve yazı tipi",
                        onClick     = onOpenAppearance,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Group 3: Kontrol ──────────────────────────────────────────
            item {
                SettingsSectionLabel("Kontrol")
                SettingsCategoryCard {
                    SettingsRow(
                        icon        = PhIcons.Regular.Headphones,
                        label       = "Arka Plan",
                        description = "Arka planda dinleme",
                        onClick     = onOpenBackground,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon        = PhIcons.Regular.Shield,
                        label       = "Otonomi",
                        description = "İşlem onay seviyesi",
                        onClick     = onOpenAutonomy,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon        = PhIcons.Regular.Gear,
                        label       = "Sistem",
                        description = "Ses ve izin yönetimi",
                        onClick     = onOpenSystem,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Group 4: Veri & Gelişmiş ──────────────────────────────────
            item {
                SettingsSectionLabel("Veri & Gelişmiş")
                SettingsCategoryCard {
                    SettingsRow(
                        icon        = PhIcons.Regular.Trash,
                        label       = "Veri",
                        description = "Sohbet geçmişini yönet",
                        onClick     = onOpenData,
                    )
                    SettingsDivider()
                    // Phase 4 — secondary accent signals advanced/power feature
                    SettingsRow(
                        icon         = PhIcons.Regular.Terminal,
                        label        = "Power Mode",
                        description  = "Gömülü Linux shell ve terminal",
                        onClick      = onOpenPowerMode,
                        useSecondary = true,
                    )
                }
            }
        }
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun IrisSettingsHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        IrisTheme.colors.primary.copy(alpha = 0.16f),
                        IrisTheme.colors.gradientEnd.copy(alpha = 0.07f),
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                IrisTheme.colors.primary,
                                IrisTheme.colors.gradientEnd,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = PhIcons.Regular.Sparkle,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text       = "IRIS",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = IrisTheme.colors.primary,
                )
                Text(
                    text  = "Kişisel AI Asistanın",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                )
            }
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text          = text.uppercase(),
        style         = MaterialTheme.typography.labelSmall,
        color         = IrisTheme.colors.primary,
        letterSpacing = 1.2.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

// ── Card container ────────────────────────────────────────────────────────────

@Composable
private fun SettingsCategoryCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

// ── Settings row ──────────────────────────────────────────────────────────────
//
// [useSecondary] = true uses IrisTheme.colors.secondary tint (for Power Mode / Phase 4 items).
// [iconTint]     explicit Color override; if null, resolves from useSecondary or primary.

@Composable
fun SettingsRow(
    icon         : ImageVector,
    label        : String,
    description  : String,
    onClick      : () -> Unit,
    iconTint     : Color?  = null,
    useSecondary : Boolean = false,
) {
    val tint = when {
        iconTint != null -> iconTint
        useSecondary     -> IrisTheme.colors.secondary
        else             -> IrisTheme.colors.primary
    }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = tint,
                modifier           = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge,
                color = ColorTextPrimary,
            )
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }
        Icon(
            imageVector        = PhIcons.Regular.CaretRight,
            contentDescription = null,
            tint               = ColorTextSecondary,
            modifier           = Modifier.size(16.dp),
        )
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────
// Start = 16dp left padding + 36dp icon + 12dp spacer = 64dp

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    )
}