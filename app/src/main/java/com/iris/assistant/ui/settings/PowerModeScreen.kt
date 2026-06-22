package com.iris.assistant.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.data.shell.ShellLine
import com.iris.assistant.domain.model.BootstrapState
import com.iris.assistant.ui.components.IrisCard
import com.iris.assistant.util.Constants

@Composable
fun PowerModeScreen(
    onBack: () -> Unit,
    viewModel: PowerModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Warning dialog — shown on first Power Mode activation
    if (state.showWarningDialog) {
        PowerModeWarningDialog(
            onConfirm = viewModel::onWarningConfirmed,
            onDismiss = viewModel::onWarningDismissed,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text  = "Power Mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(56.dp)) // balance back button
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp, vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Enable toggle ─────────────────────────────────────────────
            item {
                IrisCard {
                    Row(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = "Power Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text  = "Embedded Linux shell — run scripts, servers, terminal commands",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked         = state.powerModeEnabled,
                            onCheckedChange = viewModel::onPowerModeToggle,
                        )
                    }
                }
            }

            // ── Bootstrap status (only when Power Mode is on) ─────────────
            if (state.powerModeEnabled) {
                item {
                    BootstrapStatusCard(
                        bootstrapState  = state.bootstrapState,
                        onInstall       = viewModel::installBootstrap,
                        onUninstall     = viewModel::uninstallBootstrap,
                    )
                }

                // ── Shell security ────────────────────────────────────────
                item {
                    ShellSecurityCard(
                        current  = state.shellSecurity,
                        onChange = viewModel::setShellSecurity,
                    )
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
    IrisCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text  = "Bootstrap Environment",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedContent(targetState = bootstrapState, label = "bootstrap-state") { bState ->
                when (bState) {
                    is BootstrapState.Idle -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text  = "Not installed (~80 MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onInstall) {
                                Text("Download & Install")
                            }
                        }
                    }
                    is BootstrapState.Checking -> {
                        Row(
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text  = "Checking latest release…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is BootstrapState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val label = bState.fraction
                                ?.let { "Downloading… ${(it * 100).toInt()}%" }
                                ?: "Downloading…"
                            Text(
                                text  = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (bState.fraction != null) {
                                LinearProgressIndicator(
                                    progress = { bState.fraction!! },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    is BootstrapState.Extracting -> {
                        Row(
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text  = "Extracting…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is BootstrapState.Installed -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text  = "Installed — ${bState.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = onUninstall) {
                                Text(
                                    text  = "Uninstall",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    is BootstrapState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text  = "Error: ${bState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (bState.retryable) {
                                TextButton(onClick = onInstall) {
                                    Text("Retry")
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
    current  : String,
    onChange : (String) -> Unit,
) {
    val options = listOf(
        Triple(Constants.SHELL_SECURITY_UNRESTRICTED, "Unrestricted", "AI runs any command immediately"),
        Triple(Constants.SHELL_SECURITY_CONFIRM_EACH, "Confirm Each",  "Preview + 1 s countdown before every command"),
        Triple(Constants.SHELL_SECURITY_RESTRICTED,   "Restricted",    "Regex blacklist for destructive patterns"),
    )

    IrisCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = "Shell Security",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text  = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new output
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    IrisCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = "Terminal",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onClear) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!shellRunning) {
                        TextButton(onClick = onStart) {
                            Text("Start", color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        TextButton(onClick = onInterrupt) {
                            Text("Ctrl-C", color = MaterialTheme.colorScheme.secondary)
                        }
                        TextButton(onClick = onStop) {
                            Text("Stop", color = MaterialTheme.colorScheme.error)
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
                    .background(Color(0xFF0D0D0D)),
            ) {
                LazyColumn(
                    state   = listState,
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
                                Color(0xFFFF6B6B)
                            else
                                Color(0xFFE4E4E7),
                        )
                    }
                }
                // "Shell not running" overlay
                AnimatedVisibility(
                    visible = !shellRunning && lines.isEmpty(),
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text  = "Press Start to open a shell session",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF52525B),
                        )
                    }
                }
            }

            // Input row
            AnimatedVisibility(visible = shellRunning) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value         = input,
                        onValueChange = { input = it },
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("Enter command…", fontSize = 13.sp) },
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
                            focusedContainerColor   = Color(0xFF18181B),
                            unfocusedContainerColor = Color(0xFF18181B),
                            focusedIndicatorColor   = MaterialTheme.colorScheme.primary,
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
                        Text("Send")
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
        title = { Text("Enable Power Mode?") },
        text  = {
            Text(
                "Power Mode gives IRIS access to a full Linux shell environment. " +
                "In Unrestricted mode, the AI can execute any command without confirmation. " +
                "Only enable this if you understand and accept the risks."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Enable", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}