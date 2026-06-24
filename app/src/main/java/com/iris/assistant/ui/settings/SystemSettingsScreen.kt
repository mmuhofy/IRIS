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
import androidx.compose.ui.unit.sp
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.CaretRight
import com.phosphor.icons.regular.Lock
import com.phosphor.icons.regular.SpeakerHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    onBack                 : () -> Unit,
    onOpenVoiceSettings    : () -> Unit = {},
    onOpenPermissionManager: () -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Sistem",
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding      = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            item {
                Text(
                    text          = "SİSTEM",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = IrisTheme.colors.primary,
                    letterSpacing = 1.2.sp,
                    modifier      = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    SystemNavRow(
                        icon        = PhIcons.Regular.SpeakerHigh,
                        label       = "Ses ayarları",
                        description = "Ses karakteri ve sağlayıcı",
                        onClick     = onOpenVoiceSettings,
                    )
                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp)
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    )
                    SystemNavRow(
                        icon        = PhIcons.Regular.Lock,
                        label       = "İzin yöneticisi",
                        description = "Tüm uygulama izinlerini görüntüle",
                        onClick     = onOpenPermissionManager,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemNavRow(
    icon        : ImageVector,
    label       : String,
    description : String? = null,
    onClick     : () -> Unit,
) {
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
                .background(IrisTheme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = IrisTheme.colors.primary,
                modifier           = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = ColorTextPrimary,
            )
            if (description != null) {
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                )
            }
        }
        Icon(
            imageVector        = PhIcons.Regular.CaretRight,
            contentDescription = null,
            tint               = ColorTextSecondary,
            modifier           = Modifier.size(16.dp),
        )
    }
}