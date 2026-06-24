package com.iris.assistant.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.BatteryHigh   // UNTESTED — verify before use
import com.phosphor.icons.regular.Headphones
import com.phosphor.icons.regular.Info           // UNTESTED — verify before use

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSettingsScreen(
    onBack    : () -> Unit,
    viewModel : BackgroundSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Arka Plan",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding      = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {

            item {
                Text(
                    text          = "DİNLEME",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = IrisTheme.colors.primary,
                    letterSpacing = 1.2.sp,
                    modifier      = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            // ── Toggle card ───────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
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
                                imageVector        = PhIcons.Regular.Headphones,
                                contentDescription = null,
                                tint               = IrisTheme.colors.primary,
                                modifier           = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = "Arka planda dinle",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color      = ColorTextPrimary,
                            )
                            Text(
                                text  = "IRIS kapalıyken de \"Hey IRIS\" dinler",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                        }
                        Switch(
                            checked         = uiState.backgroundListening,
                            onCheckedChange = viewModel::onBackgroundListeningChange,
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = IrisTheme.colors.primary,
                            ),
                        )
                    }
                }
            }

            // ── Info card ─────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    // UNTESTED — BatteryHigh and Info icons need verification
                    InfoRow(
                        label = "Pil tüketimi",
                        body  = "Arka plan dinleme, Porcupine'ın düşük güç \"always-on\" " +
                                "motoru sayesinde minimum pil tüketir.",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp)
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    )
                    InfoRow(
                        label = "Pil optimizasyonu",
                        body  = "En iyi sonuç için IRIS'i pil optimizasyonu kısıtlamalarından " +
                                "muaf tutun. Sistem > İzinler ekranından yapabilirsiniz.",
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, body: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(IrisTheme.colors.secondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                // Using Headphones as safe fallback; replace with Info if available
                imageVector        = PhIcons.Regular.Headphones,
                contentDescription = null,
                tint               = IrisTheme.colors.secondary,
                modifier           = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color      = ColorTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = body,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }
    }
}