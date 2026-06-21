package com.iris.assistant.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.ArrowUp
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.Stop

// ---------------------------------------------------------------------------
// ChatScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Init - pass conversationId once (0 = create new)
    LaunchedEffect(conversationId) {
        viewModel.init(conversationId)
    }

    // Scroll to bottom whenever messages change or thinking indicator appears
    LaunchedEffect(uiState.messages.size, uiState.isThinking) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(
                index = uiState.messages.size - 1 + if (uiState.isThinking) 1 else 0
            )
        }
    }

    // Snackbar on error
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    val primary = IrisTheme.colors.primary

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Iris",
                        style = MaterialTheme.typography.titleMedium,
                        color = ColorTextSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // -----------------------------------------------------------------
            // Message list
            // -----------------------------------------------------------------
            LazyColumn(
                state           = listState,
                modifier        = Modifier.weight(1f),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.messages.isEmpty() && !uiState.isThinking) {
                    item {
                        ChatEmptyState(modifier = Modifier.fillParentMaxSize())
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                if (uiState.isThinking) {
                    item(key = "thinking") {
                        ThinkingBubble()
                    }
                }
            }

            // -----------------------------------------------------------------
            // Input bar — Google AI Edge Gallery style
            // -----------------------------------------------------------------
            ChatInputBar(
                text           = uiState.inputText,
                isThinking     = uiState.isThinking,
                isRecording    = uiState.isRecording,
                isTranscribing = uiState.isTranscribing,
                onTextChange   = viewModel::onInputChange,
                onSend         = viewModel::onSend,
                onMicToggle    = viewModel::onMicToggle,
                onStop         = viewModel::onStop,
                modifier       = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ChatBubble — user (right, primary flat color, hard corner top-right)
//              assistant (left, surface flat color, hard corner top-left)
// ---------------------------------------------------------------------------

@Composable
private fun ChatBubble(message: ChatMessage) {
    val primary = IrisTheme.colors.primary
    val isUser  = message.role == ChatMessage.Role.USER
    val r       = 18.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 48.dp else 0.dp,
                end   = if (isUser) 0.dp else 48.dp,
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    if (isUser) RoundedCornerShape(topStart = r, topEnd = 0.dp, bottomStart = r, bottomEnd = r)
                    else        RoundedCornerShape(topStart = 0.dp, topEnd = r, bottomStart = r, bottomEnd = r)
                )
                .background(
                    if (isUser) primary
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text  = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ThinkingBubble - animated 3-dot indicator
// ---------------------------------------------------------------------------

@Composable
private fun ThinkingBubble() {
    val primary = IrisTheme.colors.primary
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingDots")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 48.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                repeat(3) { idx ->
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue  = 1f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(500, delayMillis = idx * 160, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dot$idx",
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ChatEmptyState - shown when conversation has no messages yet
// ---------------------------------------------------------------------------

@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Iris",
                style = MaterialTheme.typography.headlineMedium,
                color = ColorTextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Sana nasıl yardımcı olabilirim?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ChatInputBar — Google AI Edge Gallery style (MessageInputText exact layout):
//   bordered container, TextField (transparent), two-row, IconButton send.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    text          : String,
    isThinking    : Boolean,
    isRecording   : Boolean,
    isTranscribing: Boolean,
    onTextChange  : (String) -> Unit,
    onSend        : () -> Unit,
    onMicToggle   : () -> Unit,
    onStop        : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    val primary = IrisTheme.colors.primary
    val canSend = text.isNotBlank() && !isThinking

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        // Row 1 — TextField (transparent containers, no indicator)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value       = text,
                onValueChange = onTextChange,
                modifier    = Modifier.weight(1f),
                minLines    = 1,
                maxLines    = 3,
                colors      = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor   = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent,
                    disabledContainerColor  = Color.Transparent,
                ),
                textStyle   = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        text  = "Mesaj yaz...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        // Row 2 — action button (right-aligned, offset up to overlap text row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset(y = (-8).dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            when {
                isThinking -> {
                    IconButton(
                        onClick = onStop,
                        colors  = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Icon(PhIcons.Regular.Stop, "Durdur", tint = primary)
                    }
                }
                isRecording || isTranscribing -> {
                    IconButton(
                        onClick = onMicToggle,
                        colors  = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Icon(PhIcons.Regular.Microphone, "Kaydi durdur", tint = primary)
                    }
                }
                text.isBlank() -> {
                    IconButton(
                        onClick = onMicToggle,
                    ) {
                        Icon(
                            PhIcons.Regular.Microphone,
                            contentDescription = "Sesli giriş",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        colors  = IconButtonDefaults.iconButtonColors(
                            containerColor        = primary,
                            disabledContainerColor = primary.copy(alpha = 0.3f),
                            contentColor          = Color.White,
                            disabledContentColor   = Color.White.copy(alpha = 0.5f),
                        ),
                    ) {
                        Icon(PhIcons.Regular.ArrowUp, "Gönder")
                    }
                }
            }
        }
    }
}