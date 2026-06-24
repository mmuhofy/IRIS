package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.shell.BootstrapDownloader
import com.iris.assistant.data.shell.EmbeddedShell
import com.iris.assistant.data.shell.ShellLine
import com.iris.assistant.domain.model.BootstrapState
import com.iris.assistant.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PowerModeUiState(
    val powerModeEnabled : Boolean      = false,
    val shellSecurity    : String       = Constants.SHELL_SECURITY_DEFAULT,
    val bootstrapState   : BootstrapState = BootstrapState.Idle,
    val terminalLines    : List<ShellLine> = emptyList(),
    val shellRunning     : Boolean      = false,
    // true while the first-activation warning dialog should be shown
    val showWarningDialog: Boolean      = false,
)

@HiltViewModel
class PowerModeViewModel @Inject constructor(
    private val prefsRepo         : PreferencesRepository,
    private val bootstrapInstaller: BootstrapInstaller,
    private val embeddedShell     : EmbeddedShell,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PowerModeUiState())
    val uiState: StateFlow<PowerModeUiState> = _uiState.asStateFlow()

    init {
        // Mirror persisted preferences into UI state
        viewModelScope.launch {
            prefsRepo.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        powerModeEnabled = prefs.powerModeEnabled,
                        shellSecurity    = prefs.shellSecurity,
                    )
                }
            }
        }
        // If bootstrap already installed, reflect that immediately
        if (bootstrapInstaller.isInstalled()) {
            _uiState.update {
                it.copy(
                    bootstrapState = BootstrapState.Installed(
                        prefixPath = bootstrapInstaller.prefixDir.absolutePath,
                        version    = bootstrapInstaller.installedVersion() ?: "",
                    )
                )
            }
        }
        // Stream terminal output into UI state
        viewModelScope.launch {
            embeddedShell.output.collect { line ->
                _uiState.update { state ->
                    state.copy(
                        terminalLines = (state.terminalLines + line)
                            .takeLast(Constants.TERMINAL_MAX_LINES)
                    )
                }
            }
        }
    }

    // ── Power Mode toggle ─────────────────────────────────────────────────────

    /**
     * Called when the user flips the Power Mode toggle.
     * First activation shows the warning dialog before doing anything.
     */
    fun onPowerModeToggle(enabled: Boolean) {
        if (enabled && !_uiState.value.powerModeEnabled) {
            // First enable — show warning dialog first
            _uiState.update { it.copy(showWarningDialog = true) }
        } else if (!enabled) {
            disablePowerMode()
        }
    }

    /** User confirmed the warning dialog — proceed with enabling Power Mode. */
    fun onWarningConfirmed() {
        _uiState.update { it.copy(showWarningDialog = false) }
        enablePowerMode()
    }

    fun onWarningDismissed() {
        _uiState.update { it.copy(showWarningDialog = false) }
    }

    private fun enablePowerMode() {
        viewModelScope.launch {
            prefsRepo.setPowerModeEnabled(true)
            // Kick off bootstrap install if not already installed
            if (!bootstrapDownloader.isInstalled()) {
                installBootstrap()
            }
        }
    }

    private fun disablePowerMode() {
        viewModelScope.launch {
            // Stop shell if running
            if (embeddedShell.isRunning) embeddedShell.stop()
            prefsRepo.setPowerModeEnabled(false)
        }
    }

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    fun installBootstrap() {
        viewModelScope.launch {
            bootstrapInstaller.install().collect { state ->
                _uiState.update { it.copy(bootstrapState = state) }
            }
        }
    }

    fun uninstallBootstrap() {
        viewModelScope.launch {
            if (embeddedShell.isRunning) embeddedShell.stop()
            bootstrapInstaller.uninstall()
            _uiState.update { it.copy(bootstrapState = BootstrapState.Idle) }
        }
    }

    // ── Shell session ─────────────────────────────────────────────────────────

    fun startShell() {
        viewModelScope.launch {
            runCatching { embeddedShell.start() }
                .onSuccess { _uiState.update { it.copy(shellRunning = true) } }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(
                            bootstrapState = BootstrapState.Error(
                                message   = "Failed to start shell: ${ex.message}",
                                cause     = ex,
                                retryable = true,
                            )
                        )
                    }
                }
        }
    }

    fun sendCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            runCatching { embeddedShell.send(command) }
                .onFailure { ex ->
                    _uiState.update { state ->
                        state.copy(
                            terminalLines = state.terminalLines + ShellLine(
                                text   = "Error: ${ex.message}",
                                stream = ShellLine.Stream.STDERR,
                            )
                        )
                    }
                }
        }
    }

    fun interruptShell() {
        viewModelScope.launch { embeddedShell.interrupt() }
    }

    fun stopShell() {
        viewModelScope.launch {
            embeddedShell.stop()
            _uiState.update { it.copy(shellRunning = false) }
        }
    }

    fun clearTerminal() {
        _uiState.update { it.copy(terminalLines = emptyList()) }
    }

    // ── Shell security ────────────────────────────────────────────────────────

    fun setShellSecurity(level: String) {
        viewModelScope.launch { prefsRepo.setShellSecurity(level) }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT stop the shell on ViewModel clear — the session should
        // persist while the app is in foreground. It is stopped only when
        // Power Mode is explicitly disabled or the app process dies.
    }
}