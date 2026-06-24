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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.Gear
import com.phosphor.icons.regular.Lightning   // UNTESTED — verify before use
import com.phosphor.icons.regular.Shield
import com.phosphor.icons.regular.ShieldCheck  // UNTESTED — verify before use

// ---------------------------------------------------------------------------
// Per-level metadata
// ---------------------------------------------------------------------------

private data class LevelMeta(
    val level       : AutonomyLevel,
    val icon        : ImageVector,
    val title       : String,
    val description : String,
    val riskLabel   : String,
)

// UNTESTED — icon imports above need to be verified against the installed Phosphor version.
// If ShieldCheck / Lightning do not exist, replace with Shield and Gear respectively.
private val LEVEL_METAS = listOf(
    LevelMeta(
        level       = AutonomyLevel.SAFE,
        icon        = PhIcons.Regular.Shield,
        title       = "Güvenli",
        description = "3 saniye önizleme. Yıkıcı işlemler için onay gerekir.",
        riskLabel   = "Düşük risk",
    ),
    LevelMeta(
        level       = AutonomyLevel.BALANCED,
        icon        = PhIcons.Regular.ShieldCheck,
        title       = "Dengeli",
        description = "1 saniye önizleme. Yıkıcı işlemler onay ister.",
        riskLabel   = "Orta risk",
    ),
    LevelMeta(
        level       = AutonomyLevel.FULL_AUTO,
        icon        = PhIcons.Regular.Lightning,
        title       = "Tam Otomatik",
        description = "Önizlemesiz, tüm işlemler anında gerçekleşir.",
        riskLabel   = "Yüksek risk",
    ),
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutonomySettingsScreen(
    onBack    : () -> Unit,
    viewModel : AutonomySettingsViewModel = hiltViewModel(),
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    var showWarning by remember { mutableStateOf(false) }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            shape            = RoundedCornerShape(20.dp),
            title            = {
                Text("Tam Otomatik Mod", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "Bu modda IRIS, önizleme göstermeden tüm işlemleri otomatik olarak " +
                    "gerçekleştirir. Ekran kontrolü, mesaj gönderme ve dosya işlemleri dahil " +
                    "olmak üzere hiçbir işlem için onayınız alınmaz.\n\n" +
                    "Bu modu etkinleştirmek istediğinize emin misiniz?",
                    color = ColorTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAutonomyLevelChange(AutonomyLevel.FULL_AUTO)
                    showWarning = false
                }) {
                    Text("Etkinleştir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text("İptal", color = IrisTheme.colors.primary)
                }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Otonomi",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding      = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {

            item {
                Text(
                    text          = "OTONOMİ SEVİYESİ",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = IrisTheme.colors.primary,
                    letterSpacing = 1.2.sp,
                    modifier      = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            items(
                count = LEVEL_METAS.size,
                key   = { LEVEL_METAS[it].level.name },
            ) { index ->
                val meta     = LEVEL_METAS[index]
                val selected = meta.level == uiState.autonomyLevel
                AutonomyLevelCard(
                    meta     = meta,
                    selected = selected,
                    onClick  = {
                        if (meta.level == AutonomyLevel.FULL_AUTO && !selected) {
                            showWarning = true
                        } else {
                            viewModel.onAutonomyLevelChange(meta.level)
                        }
                    },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Otonomi seviyesi, IRIS'in eylemlerini gerçekleştirmeden önce " +
                           "onayınızı alıp almayacağını belirler. Tam Otomatik modda IRIS, " +
                           "herhangi bir onay beklemeksizin tüm işlemleri yürütür.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = ColorTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Level card
// ---------------------------------------------------------------------------

@Composable
private fun AutonomyLevelCard(
    meta    : LevelMeta,
    selected: Boolean,
    onClick : () -> Unit,
) {
    // Per-level accent color: primary / secondary / error
    val accentColor = when (meta.level) {
        AutonomyLevel.SAFE      -> IrisTheme.colors.primary
        AutonomyLevel.BALANCED  -> IrisTheme.colors.secondary
        AutonomyLevel.FULL_AUTO -> MaterialTheme.colorScheme.error
    }

    val bgColor by animateColorAsState(
        targetValue   = if (selected) accentColor.copy(alpha = 0.09f) else Color.Transparent,
        animationSpec = tween(220),
        label         = "levelBg",
    )
    val borderColor by animateColorAsState(
        targetValue   = if (selected) accentColor.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        animationSpec = tween(220),
        label         = "levelBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon circle
        Box(
            modifier         = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (selected) accentColor.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = meta.icon,
                contentDescription = null,
                tint               = if (selected) accentColor
                                     else ColorTextSecondary,
                modifier           = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = meta.title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color      = if (selected) accentColor else ColorTextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                // Risk pill badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.13f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text  = meta.riskLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text  = meta.description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }
    }
}