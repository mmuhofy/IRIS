package com.iris.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.local.datastore.UserPreferences
import com.iris.assistant.ui.navigation.IrisNavGraph
import com.iris.assistant.ui.navigation.NavRoute
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.IrisTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ---------------------------------------------------------------------------
// AppViewModel — holds app-wide preferences (color scheme, start destination)
// ---------------------------------------------------------------------------
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )
}

// ---------------------------------------------------------------------------
// IrisApp — root composable
// ---------------------------------------------------------------------------
@Composable
fun IrisApp(
    viewModel: AppViewModel = hiltViewModel()
) {
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