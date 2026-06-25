package com.iris.assistant.ui.settings

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.iris.assistant.domain.model.DownloadState
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.CheckCircle
import com.phosphor.icons.regular.Cpu
import com.phosphor.icons.regular.Download
import com.phosphor.icons.regular.Trash

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelScreen(
    onBack    : () -> Unit,
    viewModel : LocalModelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // NOTE: containerColor = Color.Transparent — do NOT change to opaque.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Yerel Modeller",
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
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            // Info banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IrisTheme.colors.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector        = PhIcons.Regular.Cpu,
                        contentDescription = null,
                        tint               = IrisTheme.colors.primary,
                        modifier           = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text  = "Modeller cihazda çalışır, internet gerekmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IrisTheme.colors.primary,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            items(
                items = uiState.models,
                key   = { it.id },
            ) { model ->
                LocalModelCard(
                    model      = model,
                    isSelected = model.id == uiState.selectedModelId,
                    onSelect   = { viewModel.onSelectModel(model.id) },
                    onDownload = { viewModel.onDownloadModel(model.id) },
                    onDelete   = { viewModel.onDeleteModel(model.id) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Model card
// ---------------------------------------------------------------------------

@Composable
private fun LocalModelCard(
    model      : ModelUiItem,
    isSelected : Boolean,
    onSelect   : () -> Unit,
    onDownload : () -> Unit,
    onDelete   : () -> Unit,
) {
    val borderColor = if (isSelected) IrisTheme.colors.primary.copy(alpha = 0.45f)
                      else Color.Transparent
    val bgColor     = if (isSelected) IrisTheme.colors.primary.copy(alpha = 0.07f)
                      else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        // ── Header row ────────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Model icon
            Box(
                modifier         = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = PhIcons.Regular.Cpu,
                    contentDescription = null,
                    tint               = if (isSelected) IrisTheme.colors.primary
                                         else ColorTextSecondary,
                    modifier           = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text       = model.displayName,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (isSelected) IrisTheme.colors.primary else ColorTextPrimary,
                    )
                    if (model.recommended) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text  = "Önerilen",
                                style = MaterialTheme.typography.labelSmall,
                                color = IrisTheme.colors.secondary,
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            imageVector        = PhIcons.Regular.CheckCircle,
                            contentDescription = null,
                            tint               = IrisTheme.colors.primary,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text  = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                )
            }

            // Size pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text  = formatSize(model.sizeMb),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                )
            }
        }

        // ── Download state / actions ──────────────────────────────────────
        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = model.downloadState,
            label       = "dlState_${model.id}",
        ) { dlState ->
            when (dlState) {
                is DownloadState.Connecting -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text  = "Sunucuya bağlanıyor…",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color    = IrisTheme.colors.primary,
                        )
                    }
                }
                is DownloadState.Downloading -> {
                    val total = dlState.totalBytes.coerceAtLeast(0L)
                    val frac  = if (total > 0L) (dlState.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text  = "İndiriliyor…",
                                style = MaterialTheme.typography.bodySmall,
                                color = IrisTheme.colors.primary,
                            )
                            Text(
                                text  = if (total > 0L)
                                            "${formatBytes(dlState.bytesDownloaded)} / ${formatBytes(total)}"
                                        else
                                            formatBytes(dlState.bytesDownloaded),
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorTextSecondary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color    = IrisTheme.colors.primary,
                        )
                    }
                }
                is DownloadState.Error -> {
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = "Hata: ${dlState.message}",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDownload) {
                            Text("Tekrar Dene", color = IrisTheme.colors.primary)
                        }
                    }
                }
                DownloadState.Ready, is DownloadState.Idle -> {
                    if (model.isDownloaded) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!isSelected) {
                                // Select button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(IrisTheme.colors.primary)
                                        .clickable(onClick = onSelect)
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text       = "Kullan",
                                        style      = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = Color.White,
                                    )
                                }
                            } else {
                                // "Active" pill when already selected
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text       = "Aktif",
                                        style      = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = IrisTheme.colors.primary,
                                    )
                                }
                            }
                            // Delete icon button
                            Box(
                                modifier         = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                                    .clickable(onClick = onDelete),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector        = PhIcons.Regular.Trash,
                                    contentDescription = "Sil",
                                    tint               = MaterialTheme.colorScheme.error,
                                    modifier           = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        // Download button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    width = 1.dp,
                                    color = IrisTheme.colors.primary.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(onClick = onDownload)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector        = PhIcons.Regular.Download,
                                    contentDescription = null,
                                    tint               = IrisTheme.colors.primary,
                                    modifier           = Modifier.size(16.dp),
                                )
                                Text(
                                    text       = "İndir",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = IrisTheme.colors.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

private fun formatSize(sizeMb: Int): String = when {
    sizeMb >= 1000 -> "%.1f GB".format(sizeMb / 1000f)
    else           -> "$sizeMb MB"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000f)
    bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000         -> "%.1f KB".format(bytes / 1_000f)
    else                   -> "$bytes B"
}