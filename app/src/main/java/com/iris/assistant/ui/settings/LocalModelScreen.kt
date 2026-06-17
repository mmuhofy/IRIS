package com.iris.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.DownloadState
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelScreen(
    onBack   : () -> Unit,
    viewModel: LocalModelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yerel Modeller",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Modeller cihazınızda çalışır, internet gerekmez. İndirmek için bir modele dokunun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }

            items(uiState.models) { modelState ->
                ModelCard(
                    modelState = modelState,
                    isSelected = modelState.id == uiState.selectedModelId,
                    onSelect   = { viewModel.onSelectModel(modelState.id) },
                    onDownload = { viewModel.onDownloadModel(modelState.id) },
                    onDelete   = { viewModel.onDeleteModel(modelState.id) },
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModelCard(
    modelState: ModelUiItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgColor = if (isSelected) {
        IrisTheme.colors.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderModifier = if (isSelected) {
        Modifier.background(
            color = IrisTheme.colors.primary.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
        )
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(borderModifier)
            .background(bgColor)
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = modelState.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTextPrimary,
                        )
                        if (modelState.recommended) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Önerilen",
                                style = MaterialTheme.typography.labelSmall,
                                color = IrisTheme.colors.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = modelState.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextSecondary,
                    )
                    Text(
                        text = formatSize(modelState.sizeMb),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTextSecondary,
                    )
                }

                Spacer(Modifier.width(12.dp))

                when (val dlState = modelState.downloadState) {
                    is DownloadState.Downloading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val total = dlState.totalBytes.coerceAtLeast(0L)
                            val frac = if (total > 0L) (dlState.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier.width(80.dp),
                                color = IrisTheme.colors.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            if (dlState.progress >= 1f) {
                                Text(
                                    text = "${dlState.progress.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorTextSecondary,
                                )
                            } else {
                                Text(
                                    text = if (total > 0L) {
                                        "${formatBytes(dlState.bytesDownloaded)} / ${formatBytes(total)}"
                                    } else {
                                        formatBytes(dlState.bytesDownloaded)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorTextSecondary,
                                )
                            }
                        }
                    }
                    is DownloadState.Error -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IrisTheme.colors.primary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Tekrar Dene", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DownloadState.Ready, is DownloadState.Idle -> {
                        if (modelState.isDownloaded) {
                            Row {
                                if (!isSelected) {
                                    Button(
                                        onClick = onSelect,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = IrisTheme.colors.primary,
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Text("Seç", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Button(
                                    onClick = onDelete,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(
                                        PhIcons.Regular.Trash,
                                        contentDescription = "Sil",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onDownload,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IrisTheme.colors.primary,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    PhIcons.Regular.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("İndir", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(sizeMb: Int): String {
    return when {
        sizeMb >= 1000 -> "%.1f GB".format(sizeMb / 1000f)
        else           -> "${sizeMb} MB"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000f)
        bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000f)
        bytes >= 1_000         -> "%.1f KB".format(bytes / 1_000f)
        else                   -> "${bytes} B"
    }
}
