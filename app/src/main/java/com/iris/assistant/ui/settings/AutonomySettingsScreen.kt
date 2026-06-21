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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutonomySettingsScreen(
    onBack: () -> Unit,
    viewModel: AutonomySettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showWarning by remember { mutableStateOf(false) }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Tam Otomatik Mod") },
            text = {
                Text(
                    "Bu modda IRIS, önizleme göstermeden tüm işlemleri otomatik olarak " +
                    "gerçekleştirir. Ekran kontrolü, mesaj gönderme ve dosya işlemleri dahil " +
                    "olmak üzere hiçbir işlem için onayınız alınmaz.\n\n" +
                    "Bu modu etkinleştirmek istediğinize emin misiniz?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAutonomyLevelChange(AutonomyLevel.FULL_AUTO)
                    showWarning = false
                }) {
                    Text("Etkinleştir", color = IrisTheme.colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text("İptal")
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
                        text = "Otonomi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            item {
                Spacer(Modifier.height(4.dp))

                Text(
                    text = "OTONOMİ SEVİYESİ",
                    style = MaterialTheme.typography.labelSmall,
                    color = IrisTheme.colors.primary,
                    letterSpacing = TextUnit(value = 1.2f, type = TextUnitType.Sp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )

                AutonomyLevel.entries.forEach { level ->
                    val selected = level == uiState.autonomyLevel
                    val description = when (level) {
                        AutonomyLevel.SAFE -> "3 saniye önizleme süresi. Yıkıcı işlemler için onay gerekir."
                        AutonomyLevel.BALANCED -> "1 saniye önizleme süresi. Yıkıcı işlemler için onay gerekir."
                        AutonomyLevel.FULL_AUTO -> "Önizlemesiz, tüm işlemler otomatik olarak gerçekleşir."
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                if (level == AutonomyLevel.FULL_AUTO && !selected) {
                                    showWarning = true
                                } else {
                                    viewModel.onAutonomyLevelChange(level)
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { mod ->
                                    if (selected) mod.background(
                                        IrisTheme.colors.primary.copy(alpha = 0.08f),
                                        RoundedCornerShape(16.dp),
                                    )
                                    else mod.background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(16.dp),
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = level.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) IrisTheme.colors.primary
                                                else ColorTextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selected) {
                                        Icon(
                                            imageVector = PhIcons.Regular.Check,
                                            contentDescription = null,
                                            tint = IrisTheme.colors.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorTextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "SAFE: Varsayılan güvenli mod. IRIS her ekran işlemini 3 saniye önizler, " +
                           "yıkıcı işlemler (silme, gönderme, ödeme) için manuel onay ister.\n\n" +
                           "BALANCED: Normal işlemler anında gerçekleşir, yıkıcı işlemler önizlenir.\n\n" +
                           "FULL AUTO: Tüm işlemler otomatik. Bu modu etkinleştirmeden önce " +
                           "bir uyarı gösterilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
