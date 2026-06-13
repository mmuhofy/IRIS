package com.iris.assistant.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = Constants.DATASTORE_NAME)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val COLOR_SCHEME         = stringPreferencesKey("color_scheme")
        val BACKGROUND_LISTENING = booleanPreferencesKey("background_listening")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            colorScheme = prefs[Keys.COLOR_SCHEME]
                ?.let { runCatching { ColorSchemeOption.valueOf(it) }.getOrDefault(ColorSchemeOption.LAVENDER) }
                ?: ColorSchemeOption.LAVENDER,
            backgroundListening = prefs[Keys.BACKGROUND_LISTENING] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun setColorScheme(scheme: ColorSchemeOption) {
        context.dataStore.edit { it[Keys.COLOR_SCHEME] = scheme.name }
    }

    suspend fun setBackgroundListening(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_LISTENING] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }
}