package com.iris.assistant.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme

@Composable
fun OnboardingWelcomeScreen(onNext: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "Merhaba, Muhofy.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = "Ben IRIS. Hazır olduğunda başlayalım.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        // TODO: Replace with IrisButton component (Phase 1 components)
        androidx.compose.material3.Button(
            onClick = onNext,
            colors  = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = IrisTheme.colors.primary
            )
        ) {
            Text("Başla")
        }
    }
}