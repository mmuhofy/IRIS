package com.iris.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.local.datastore.UserPreferences
import com.iris.assistant.ui.navigation.IrisNavGraph
import com.iris.assistant.ui.navigation.NavRoute
import com.iris.assistant.ui.theme.IrisTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.first()
            _isReady.value = true
        }
    }

}

@Composable
fun IrisApp(
    viewModel: AppViewModel = hiltViewModel()
) {
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    if (!isReady) return

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val startDestination = if (preferences.onboardingCompleted)
        NavRoute.Home.route
    else
        NavRoute.OnboardingWelcome.route

    IrisTheme(colorSchemeOption = preferences.colorScheme) {
        IrisNavGraph(
            navController    = navController,
            startDestination = startDestination
        )
    }
}
