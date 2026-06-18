package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.assistant.ui.home.IrisCoreAnimation
import com.iris.assistant.ui.home.IrisCoreState
import com.iris.assistant.ui.theme.IrisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AssistantActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AssistantActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "onCreate")

        setContent {
            IrisTheme {
                AssistantScreen(
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    onClose: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            kotlinx.coroutines.delay(1500)
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1C1C1E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = {
                    viewModel.stop()
                    onClose()
                }) {
                    Text(
                        text = "\u2715",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(32.dp)
            )

            IrisCoreAnimation(
                state = state.coreState,
                amplitude = state.amplitude,
                coreSize = 96.dp
            )

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = state.statusText,
                color = Color(0xCCFFFFFF),
                fontSize = 16.sp
            )

            if (state.transcript.isNotBlank()) {
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = state.transcript,
                    color = Color(0x99FFFFFF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (state.response.isNotBlank()) {
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = state.response,
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
