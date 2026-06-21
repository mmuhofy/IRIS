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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.phosphor.icons.regular.Trash
import com.phosphor.icons.regular.Waveform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenModel: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenBackground: () -> Unit = {},
    onOpenAutonomy: () -> Unit = {},
    onOpenSystem: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenData: () -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item {
                SettingsCategoryCard {
                    SettingsRow(
                        icon = PhIcons.Regular.Waveform,
                        label = "Ses",
                        description = "Ses karakteri, sağlayıcı seçimi",
                        onClick = onOpenVoice,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = PhIcons.Regular.Cpu,
                        label = "Model",
                        description = "AI sağlayıcı ve model seçimi",
                        onClick = onOpenModel,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = PhIcons.Regular.Palette,
                        label = "Görünüm",
                        description = "Renk teması",
                        onClick = onOpenAppearance,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = PhIcons.Regular.Headphones,
                        label = "Arka Plan",
                        description = "Arka planda dinleme",
                        onClick = onOpenBackground,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = PhIcons.Regular.Shield,
                        label = "Otonomi",
                        description = "İşlem onay seviyesi",
                        onClick = onOpenAutonomy,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = PhIcons.Regular.Gear,
                        label = "Sistem",
                        description = "Ses ayarları, izin yöneticisi",
                        onClick = onOpenSystem,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = PhIcons.Regular.Trash,
                        label = "Veri",
                        description = "Sohbet geçmişini temizle",
                        onClick = onOpenData,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(IrisTheme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IrisTheme.colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = ColorTextPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }
        Icon(
            imageVector = PhIcons.Regular.CaretRight,
            contentDescription = null,
            tint = ColorTextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    )
}
