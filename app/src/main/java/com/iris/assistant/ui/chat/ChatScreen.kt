package com.iris.assistant.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.ArrowUp
import com.phosphor.icons.regular.List
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.Stop

// ---------------------------------------------------------------------------
// ChatScreen
// ---------------------------------------------------------------------------

@Composable
fun ChatScreen(
    conversationId: Long,
    onOpenDrawer: () -> Unit,
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
    val gradientEnd = IrisTheme.colors.gradientEnd

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // -----------------------------------------------------------------
            // Top bar
            // -----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hamburger - opens drawer (same as HomeScreen)
                TopBarIconButton(
                    icon               = PhIcons.Regular.List,
                    contentDescription = "Menu",
                    onClick            = onOpenDrawer,
                )

                Spacer(Modifier.width(4.dp))

                // Back button
                TopBarIconButton(
                    icon               = PhIcons.Regular.ArrowLeft,
                    contentDescription = "Geri",
                    onClick            = onBack,
                )

                Spacer(Modifier.width(12.dp))

                // Conversation title - auto-generated from first message
                // For now shows "Yeni Sohbet" until title is generated.
                // DrawerViewModel updates title reactively; no extra call needed here.
                Text(
                    text = "Iris",
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorTextSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // -----------------------------------------------------------------
            // Message list
            // -----------------------------------------------------------------
            LazyColumn(
                state           = listState,
                modifier        = Modifier.weight(1f),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Empty state
                if (uiState.messages.isEmpty() && !uiState.isThinking) {
                    item {
                        ChatEmptyState(modifier = Modifier.fillParentMaxSize())
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                // Thinking indicator - animated dots
                if (uiState.isThinking) {
                    item(key = "thinking") {
                        ThinkingBubble()
                    }
                }
            }

            // -----------------------------------------------------------------
            // Input bar
            // -----------------------------------------------------------------
            ChatInputBar(
                text          = uiState.inputText,
                isThinking    = uiState.isThinking,
                isRecording   = uiState.isRecording,
                isTranscribing = uiState.isTranscribing,
                onTextChange  = viewModel::onInputChange,
                onSend        = viewModel::onSend,
                onMicToggle   = viewModel::onMicToggle,
                onStop        = viewModel::onStop,
                modifier      = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ChatBubble - user (right, gradient) / assistant (left, surface)
// ---------------------------------------------------------------------------

@Composable
private fun ChatBubble(message: ChatMessage) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val isUser = message.role == ChatMessage.Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            // User bubble - gradient background, right-aligned
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart    = 18.dp,
                            topEnd      = 4.dp,
                            bottomStart = 18.dp,
                            bottomEnd   = 18.dp,
                        )
                    )
                    .background(
                        Brush.linearGradient(listOf(primary, gradientEnd))
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text  = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        } else {
            // Assistant bubble - surface background, left-aligned
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 4.dp,
                    topEnd      = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd   = 18.dp,
                ),
                color         = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    text     = message.content,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart    = 4.dp,
                topEnd      = 18.dp,
                bottomStart = 18.dp,
                bottomEnd   = 18.dp,
            ),
            color           = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                text  = "Nasil yardimci olabilirim?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ChatInputBar
// ---------------------------------------------------------------------------

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
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val canSend     = text.isNotBlank() && !isThinking

    Surface(
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        shape           = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier        = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Text field
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSend) onSend() }
                ),
                maxLines = 5,
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text  = "Mesaj yaz...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                },
            )

            Spacer(Modifier.width(8.dp))

            // Right action button - context-aware:
            // - Stop (isThinking)
            // - Send (text not empty)
            // - Mic (default / recording / transcribing)
            when {
                isThinking -> {
                    InputActionButton(
                        icon               = PhIcons.Regular.Stop,
                        contentDescription = "Durdur",
                        background         = Color(0xFFF87171), // error red
                        tint               = Color.White,
                        onClick            = onStop,
                    )
                }
                canSend -> {
                    InputActionButton(
                        icon               = PhIcons.Regular.ArrowUp,
                        contentDescription = "Gonder",
                        background         = Brush.linearGradient(listOf(primary, gradientEnd)),
                        tint               = Color.White,
                        onClick            = onSend,
                    )
                }
                else -> {
                    val micTint = when {
                        isRecording    -> Color.White
                        isTranscribing -> primary.copy(alpha = 0.5f)
                        else           -> primary
                    }
                    val micBg: Any = when {
                        isRecording -> Brush.linearGradient(listOf(primary, gradientEnd))
                        else        -> primary.copy(alpha = 0.12f)
                    }
                    InputActionButton(
                        icon               = PhIcons.Regular.Microphone,
                        contentDescription = if (isRecording) "Kaydi durdur" else "Sesli giris",
                        background         = micBg,
                        tint               = micTint,
                        onClick            = onMicToggle,
                        enabled            = !isTranscribing,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// InputActionButton - shared send/mic/stop button shape
// ---------------------------------------------------------------------------

@Composable
private fun InputActionButton(
    icon              : ImageVector,
    contentDescription: String,
    background        : Any, // Color or Brush
    tint              : Color,
    onClick           : () -> Unit,
    enabled           : Boolean = true,
) {
    val bgModifier = when (background) {
        is Brush -> Modifier.background(background, CircleShape)
        is Color -> Modifier.background(background, CircleShape)
        else     -> Modifier.background(Color.Transparent, CircleShape)
    }

    Surface(
        onClick  = onClick,
        shape    = CircleShape,
        color    = Color.Transparent,
        enabled  = enabled,
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(bgModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = contentDescription,
                tint               = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// TopBarIconButton - shared icon button shape for ChatScreen top bar
// ---------------------------------------------------------------------------

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}