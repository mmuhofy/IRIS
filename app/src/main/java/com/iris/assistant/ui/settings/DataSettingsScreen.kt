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
import com.phosphor.icons.regular.CheckCircle
import com.phosphor.icons.regular.Trash

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onBack    : () -> Unit,
    viewModel : DataSettingsViewModel = hiltViewModel(),
) {
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            shape            = RoundedCornerShape(20.dp),
            title            = {
                Text("Geçmişi Temizle", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "Tüm sohbet geçmişi silinecek. Bu işlem geri alınamaz.",
                    color = ColorTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearHistory()
                    showDialog = false
                }) {
                    Text("Kalıcı Olarak Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
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
                        text       = "Veri",
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
                    text          = "SOHBET GEÇMİŞİ",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = IrisTheme.colors.primary,
                    letterSpacing = 1.2.sp,
                    modifier      = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            // ── Success banner (shown after clearing) ─────────────────────
            if (uiState.historyCleared) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(IrisTheme.colors.primary.copy(alpha = 0.10f))
                            .border(
                                width = 1.dp,
                                color = IrisTheme.colors.primary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector        = PhIcons.Regular.CheckCircle,
                            contentDescription = null,
                            tint               = IrisTheme.colors.primary,
                            modifier           = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text  = "Sohbet geçmişi temizlendi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IrisTheme.colors.primary,
                        )
                    }
                }
            }

            // ── Destructive action card ───────────────────────────────────
            item {
                val errorColor = MaterialTheme.colorScheme.error
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(errorColor.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            color = errorColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { showDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier         = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(errorColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = PhIcons.Regular.Trash,
                            contentDescription = null,
                            tint               = errorColor,
                            modifier           = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "Sohbet geçmişini temizle",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color      = errorColor,
                        )
                        Text(
                            text  = "Tüm konuşmalar silinir, geri alınamaz",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                        )
                    }
                }
            }

            // ── Info text ─────────────────────────────────────────────────
            item {
                Text(
                    text = "Sohbet geçmişi yalnızca bu cihazda yerel olarak saklanır. " +
                           "Buluta senkronize edilmez ve bu işlem geri alınamaz.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = ColorTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}