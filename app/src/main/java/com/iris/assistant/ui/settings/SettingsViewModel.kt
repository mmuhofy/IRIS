package com.iris.assistant.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.ui.theme.AppFont
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val colorScheme        : ColorSchemeOption  = ColorSchemeOption.COBALT,
    val backgroundListening: Boolean            = true,
    val ttsVoice           : TtsVoice           = TtsVoice.DEFAULT,
    val llmProvider        : String             = Constants.LLM_PROVIDER_GEMINI,
    val llmModel           : String             = Constants.GEMINI_MODEL,
    val autonomyLevel      : AutonomyLevel      = AutonomyLevel.SAFE,
    val ttsProvider        : TtsProviderType    = TtsProviderType.GEMINI,
    val historyCleared     : Boolean            = false,
    val localModelName     : String             = "",
    val localModelPath     : String             = "",
    val fontFamily         : AppFont            = AppFont.Inter,
    val customFonts        : List<AppFont.Custom> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository : PreferencesRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _customFonts = MutableStateFlow(loadCustomFonts())
    val customFonts: StateFlow<List<AppFont.Custom>> = _customFonts.asStateFlow()

    // NOTE: ROOT CAUSE FIX — uiState's stateIn() previously used
    // initialValue = SettingsUiState(), i.e. the data class's hardcoded
    // defaults (Lavender, SAFE, Gemini, etc). Every time this screen entered
    // composition (including re-entry after the WhileSubscribed(5_000)
    // window closed), the real DataStore value hadn't arrived yet for the
    // first frame(s), so the UI briefly showed defaults before snapping to
    // the actual saved preferences. Confirmed by Muhofy: visible on every
    // Settings entry as a "flash to default, then jump to real value."
    //
    // Fix: `loaded` starts false and flips true only once a real DataStore
    // emission has been mapped into uiState (via onEach below). SettingsScreen
    // gates rendering on `loaded` so it shows nothing (not wrong defaults)
    // until the real value is available — this is normally sub-frame on a
    // local DataStore read, so no spinner is needed.
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = preferencesRepository.preferences
        .map { prefs ->
            val customFontsNow = _customFonts.value
            val resolvedFont = AppFont.fromKey(prefs.fontFamilyKey, customFontsNow)
            SettingsUiState(
                colorScheme         = prefs.colorScheme,
                backgroundListening = prefs.backgroundListening,
                ttsVoice            = prefs.ttsVoice,
                ttsProvider         = prefs.ttsProvider,
                llmProvider         = prefs.llmProvider,
                llmModel            = prefs.llmModel,
                autonomyLevel       = prefs.autonomyLevel,
                localModelName      = prefs.localModelName,
                localModelPath      = prefs.localModelPath,
                fontFamily          = resolvedFont,
                customFonts         = customFontsNow
            )
        }
        .onEach { _loaded.value = true }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    private fun loadCustomFonts(): List<AppFont.Custom> {
        val dir = AppFont.customDir(context)
        return dir.listFiles()
            ?.filter { it.extension.lowercase() in setOf("ttf", "otf") }
            ?.mapNotNull { file ->
                val name = file.nameWithoutExtension
                kotlin.runCatching { AppFont.Custom(file.absolutePath, name) }.getOrNull()
            }
            ?: emptyList()
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            try {
                val dir = AppFont.customDir(context)
                val fileName = "custom_${System.currentTimeMillis()}.ttf"
                val dest = File(dir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                _customFonts.value = loadCustomFonts()
            } catch (_: Exception) { }
        }
    }

    fun removeCustomFont(font: AppFont.Custom) {
        viewModelScope.launch {
            try {
                File(font.filePath).delete()
                _customFonts.value = loadCustomFonts()
                if (uiState.value.fontFamily.key == font.key) {
                    preferencesRepository.setFontFamily(AppFont.SystemDefault)
                }
            } catch (_: Exception) { }
        }
    }

    fun onColorSchemeChange(scheme: ColorSchemeOption) {
        viewModelScope.launch { preferencesRepository.setColorScheme(scheme) }
    }

    fun onBackgroundListeningChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBackgroundListening(enabled) }
    }

    fun onTtsVoiceChange(voice: TtsVoice) {
        viewModelScope.launch { preferencesRepository.setTtsVoice(voice) }
    }

    fun onTtsProviderChange(provider: TtsProviderType) {
        viewModelScope.launch { preferencesRepository.setTtsProvider(provider) }
    }

    fun onLlmProviderChange(provider: String) {
        viewModelScope.launch {
            preferencesRepository.setLlmProvider(provider)
            val defaultModel = Constants.defaultModelForProvider(provider)
            if (defaultModel != null) {
                preferencesRepository.setLlmModel(defaultModel.apiName)
            }
        }
    }

    fun onLlmModelChange(model: String) {
        viewModelScope.launch { preferencesRepository.setLlmModel(model) }
    }

    fun onAutonomyLevelChange(level: AutonomyLevel) {
        viewModelScope.launch { preferencesRepository.setAutonomyLevel(level) }
    }

    fun onFontFamilyChange(font: AppFont) {
        viewModelScope.launch { preferencesRepository.setFontFamily(font) }
    }

    fun onClearHistory() {
        viewModelScope.launch { conversationRepository.clearAll() }
    }
}