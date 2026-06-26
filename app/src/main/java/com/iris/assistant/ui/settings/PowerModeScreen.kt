package com.iris.assistant.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.data.shell.ShellLine
import com.iris.assistant.domain.model.BootstrapState
import com.iris.assistant.ui.theme.ColorError
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerModeScreen(
    onBack    : () -> Unit,
    viewModel : PowerModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Warning dialog — shown on first Power Mode activation
    if (state.showWarningDialog) {
        PowerModeWarningDialog(
            onConfirm = viewModel::onWarningConfirmed,
            onDismiss = viewModel::onWarningDismissed,
        )
    }

    // NOTE: containerColor = Color.Transparent to preserve nav transition animations.
    // imePadding() is applied on the LazyColumn to handle keyboard for the terminal input.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Power Mode",
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
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),       // handles keyboard visibility for terminal input
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Enable toggle ─────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = "Power Mode",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color      = ColorTextPrimary,
                            )
                            Text(
                                text  = "Gömülü Linux shell — script, sunucu, terminal komutları",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked         = state.powerModeEnabled,
                            onCheckedChange = viewModel::onPowerModeToggle,
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = IrisTheme.colors.secondary,
                            ),
                        )
                    }
                }
            }

            // ── Bootstrap status ──────────────────────────────────────────
            if (state.powerModeEnabled) {
                item {
                    BootstrapStatusCard(
                        bootstrapState = state.bootstrapState,
                        onInstall      = viewModel::installBootstrap,
                        onUninstall    = viewModel::uninstallBootstrap,
                    )
                }

                // ── Shell security ────────────────────────────────────────
                item {
                    ShellSecurityCard(
                        current  = state.shellSecurity,
                        onChange = viewModel::setShellSecurity,
                    )
                }

                // ── Storage permission (if not granted) ────────────────────
                if (!viewModel.allFilesAccessGranted) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text       = "Tüm Dosyalara Erişim",
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = ColorTextPrimary,
                                    )
                                    Text(
                                        text  = "Shell'in harici depolamaya erişmesi için gerekli",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ColorTextSecondary,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = viewModel::requestAllFilesAccess) {
                                    Text("İzin Ver", color = IrisTheme.colors.secondary)
                                }
                            }
                        }
                    }
                }

                // ── Terminal (only when bootstrap installed) ───────────────
                if (state.bootstrapState is BootstrapState.Installed) {
                    item {
                        TerminalCard(
                            lines        = state.terminalLines,
                            shellRunning = state.shellRunning,
                            onStart      = viewModel::startShell,
                            onStop       = viewModel::stopShell,
                            onInterrupt  = viewModel::interruptShell,
                            onSend       = viewModel::sendCommand,
                            onClear      = viewModel::clearTerminal,
                        )
                    }
                }
            }

            // Bottom padding
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Bootstrap status card ─────────────────────────────────────────────────────

@Composable
private fun BootstrapStatusCard(
    bootstrapState : BootstrapState,
    onInstall      : () -> Unit,
    onUninstall    : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text       = "Bootstrap Environment",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = ColorTextPrimary,
            )
            AnimatedContent(targetState = bootstrapState, label = "bootstrap-state") { bState ->
                when (bState) {
                    is BootstrapState.Idle -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text  = "Kurulu değil (~80 MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                            OutlinedButton(onClick = onInstall) {
                                Text(
                                    text  = "İndir & Kur",
                                    color = IrisTheme.colors.secondary,
                                )
                            }
                        }
                    }
                    is BootstrapState.Checking -> {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = IrisTheme.colors.secondary,
                            )
                            Text(
                                text  = "Son sürüm kontrol ediliyor…",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                        }
                    }
                    is BootstrapState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val label = bState.fraction
                                ?.let { "İndiriliyor… ${(it * 100).toInt()}%" }
                                ?: "İndiriliyor…"
                            Text(
                                text  = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                            if (bState.fraction != null) {
                                LinearProgressIndicator(
                                    progress = { bState.fraction!! },
                                    modifier = Modifier.fillMaxWidth(),
                                    color    = IrisTheme.colors.secondary,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color    = IrisTheme.colors.secondary,
                                )
                            }
                        }
                    }
                    is BootstrapState.Extracting -> {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = IrisTheme.colors.secondary,
                            )
                            Text(
                                text  = "Çıkartılıyor…",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                        }
                    }
                    is BootstrapState.InstallingPackages -> {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = IrisTheme.colors.secondary,
                            )
                            Text(
                                text  = "${bState.packageName} kuruluyor…",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTextSecondary,
                            )
                        }
                    }
                    is BootstrapState.Installed -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text  = "Kurulu — ${bState.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IrisTheme.colors.secondary,
                            )
                            TextButton(onClick = onUninstall) {
                                Text(
                                    text  = "Kaldır",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    is BootstrapState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text  = "Hata: ${bState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (bState.retryable) {
                                TextButton(onClick = onInstall) {
                                    Text("Tekrar Dene", color = IrisTheme.colors.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shell security card ───────────────────────────────────────────────────────

@Composable
private fun ShellSecurityCard(
    current : String,
    onChange: (String) -> Unit,
) {
    val options = listOf(
        Triple(Constants.SHELL_SECURITY_UNRESTRICTED, "Kısıtsız",       "AI her komutu anında çalıştırır"),
        Triple(Constants.SHELL_SECURITY_CONFIRM_EACH, "Her Komut Onayı", "Her komut için 1 sn önizleme gösterir"),
        Triple(Constants.SHELL_SECURITY_RESTRICTED,   "Kısıtlı",        "Tehlikeli pattern'ler kara listeyle engellenir"),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text       = "Shell Güvenliği",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = ColorTextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            options.forEach { (key, label, description) ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = current == key,
                        onClick  = { onChange(key) },
                        colors   = RadioButtonDefaults.colors(
                            selectedColor   = IrisTheme.colors.secondary,
                            unselectedColor = ColorTextSecondary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text       = label,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (current == key) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (current == key) ColorTextPrimary else ColorTextSecondary,
                        )
                        Text(
                            text  = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

// ── Terminal card ─────────────────────────────────────────────────────────────

@Composable
private fun TerminalCard(
    lines        : List<ShellLine>,
    shellRunning : Boolean,
    onStart      : () -> Unit,
    onStop       : () -> Unit,
    onInterrupt  : () -> Unit,
    onSend       : (String) -> Unit,
    onClear      : () -> Unit,
) {
    var input     by remember { mutableStateOf("") }
    val listState  = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "Terminal",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = ColorTextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onClear) {
                        Text(
                            text  = "Temizle",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorTextSecondary,
                        )
                    }
                    if (!shellRunning) {
                        TextButton(onClick = onStart) {
                            Text("Başlat", color = IrisTheme.colors.secondary)
                        }
                    } else {
                        TextButton(onClick = onInterrupt) {
                            Text("Ctrl-C", color = IrisTheme.colors.primary)
                        }
                        TextButton(onClick = onStop) {
                            Text("Durdur", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Output area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                LazyColumn(
                    state    = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(lines) { line ->
                        Text(
                            text  = line.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = if (line.stream == ShellLine.Stream.STDERR)
                                ColorError
                            else
                                MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (!shellRunning && lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text  = "Başlat'a basarak bir shell oturumu aç",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                        )
                    }
                }
            }

            // Input row
            AnimatedVisibility(visible = shellRunning) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value         = input,
                        onValueChange = { input = it },
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("Komut gir…", fontSize = 13.sp) },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            onSend(input)
                            input = ""
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor   = IrisTheme.colors.secondary,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    TextButton(
                        onClick = {
                            onSend(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Text("Gönder", color = IrisTheme.colors.secondary)
                    }
                }
            }
        }
    }
}

// ── Warning dialog ────────────────────────────────────────────────────────────

@Composable
private fun PowerModeWarningDialog(
    onConfirm : () -> Unit,
    onDismiss : () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text("Power Mode'u etkinleştir?", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(
                "Power Mode, IRIS'e tam bir Linux shell ortamına erişim sağlar. " +
                "Kısıtsız modda AI, onay beklemeden herhangi bir komutu çalıştırabilir. " +
                "Yalnızca riskleri anlıyor ve kabul ediyorsan etkinleştir.",
                color = ColorTextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Etkinleştir", color = IrisTheme.colors.secondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = IrisTheme.colors.primary)
            }
        },
    )
}