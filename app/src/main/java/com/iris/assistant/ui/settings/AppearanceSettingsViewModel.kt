package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.ui.theme.AppFont
import com.iris.assistant.ui.theme.ColorSchemeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppearanceSettingsUiState(
    val colorScheme : ColorSchemeOption = ColorSchemeOption.SLATE,
    val isDarkMode  : Boolean           = true,
    val fontFamily  : AppFont           = AppFont.SystemDefault,
)

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AppearanceSettingsUiState> = preferencesRepository.preferences
        .map { prefs ->
            AppearanceSettingsUiState(
                colorScheme = prefs.colorScheme,
                isDarkMode  = prefs.isDarkMode,
                fontFamily  = prefs.fontFamily,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppearanceSettingsUiState(),
        )

    fun onColorSchemeChange(scheme: ColorSchemeOption) {
        viewModelScope.launch { preferencesRepository.setColorScheme(scheme) }
    }

    fun onDarkModeChange(isDark: Boolean) {
        viewModelScope.launch { preferencesRepository.setIsDarkMode(isDark) }
    }

    fun onFontChange(font: AppFont) {
        viewModelScope.launch { preferencesRepository.setFontFamily(font) }
    }
}
