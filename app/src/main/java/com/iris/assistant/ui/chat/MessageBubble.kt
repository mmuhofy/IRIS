package com.iris.assistant.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.ui.theme.ColorSurface
import com.iris.assistant.ui.theme.IrisTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 18.dp,
                    topEnd      = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd   = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) IrisTheme.colors.primary else ColorSurface,
                tonalElevation = if (isUser) 0.dp else 2.dp
            ) {
                Text(
                    text    = message.content,
                    style   = MaterialTheme.typography.bodyMedium,
                    color   = if (isUser) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Text(
                text    = timeFormat.format(Date(message.timestampMs)),
                style   = MaterialTheme.typography.labelSmall,
                color   = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}