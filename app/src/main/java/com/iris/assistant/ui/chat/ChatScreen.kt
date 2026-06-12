package com.iris.assistant.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ChatScreen(
    onBack        : () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier          = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment  = Alignment.Center
        ) {
            // TODO: Message list (LazyColumn) — Phase 1
            // TODO: Input field + send button — Phase 1
            // TODO: Top bar with back + settings — Phase 1
            Text(
                text  = "Chat Mode — Phase 1",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}