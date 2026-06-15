package com.iris.assistant.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*
import com.phosphor.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.components.IrisButtonPrimary
import com.iris.assistant.ui.theme.IrisTheme

@Composable
fun OnboardingWelcomeScreen(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(currentStep = 1, totalSteps = 5)

        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = PhIcons.Regular.Smiley,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = IrisTheme.colors.primary
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Merhaba!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = { Text("Adın nedir?") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = PhIcons.Regular.User,
                    contentDescription = null,
                    tint = IrisTheme.colors.primary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IrisTheme.colors.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                focusedLabelColor = IrisTheme.colors.primary,
                cursorColor = IrisTheme.colors.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Ben IRIS. Senin kişisel asistanın.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        IrisButtonPrimary(
            text = "Başla",
            onClick = onNext,
            enabled = userName.isNotBlank(),
            icon = PhIcons.Filled.ArrowRightFill,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp)
        )
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            val isCompleted = step < currentStep
            val isCurrent = step == currentStep

            val dotColor by animateColorAsState(
                targetValue = when {
                    isCompleted -> IrisTheme.colors.primary.copy(alpha = 0.5f)
                    isCurrent -> IrisTheme.colors.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                },
                animationSpec = tween(durationMillis = 300),
                label = "dotColor"
            )

            val dotSize by animateDpAsState(
                targetValue = if (isCurrent) 10.dp else 8.dp,
                animationSpec = tween<Dp>(durationMillis = 300),
                label = "dotSize"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            if (index < totalSteps - 1) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
