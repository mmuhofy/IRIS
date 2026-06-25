package com.iris.assistant.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.components.IrisButtonPrimary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.ArrowRightFill
import com.phosphor.icons.regular.Eye
import com.phosphor.icons.regular.Lightning
import com.phosphor.icons.regular.Lock
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.User

// ═══════════════════════════════════════════════════════════════════════════════
// Shared onboarding UI primitives
// (Used by both OnboardingWelcomeScreen and OnboardingStepScreen)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Horizontal segmented progress bar.
 * Completed + current steps → primary color. Future steps → muted surface.
 */
@Composable
fun SegmentedStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(totalSteps) { index ->
            val active = index + 1 <= currentStep
            val segColor by animateColorAsState(
                targetValue = if (active) IrisTheme.colors.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                animationSpec = tween(durationMillis = 300),
                label = "seg_color_$index"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(segColor)
            )
        }
    }
}

/**
 * Gradient-ring icon hero container.
 * Replaces the old nested-circles pattern across all onboarding screens.
 * Uses a sweep gradient border around a soft glow background.
 */
@Composable
fun OnboardingIconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerSize: Dp = 110.dp,
    iconSize: Dp = 44.dp
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(primary.copy(alpha = 0.09f))
            .border(
                width = 1.5.dp,
                brush = Brush.sweepGradient(listOf(primary, gradientEnd, primary)),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(iconSize),
            tint               = primary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Welcome Screen — Step 1
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingWelcomeScreen(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Entrance: content slides up + fades in on first composition
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val contentAlpha by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label         = "welcomeAlpha"
    )
    val contentOffsetY by animateDpAsState(
        targetValue   = if (appeared) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label         = "welcomeOffsetY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IrisTheme.colors.primary.copy(alpha = 0.07f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY   = 520f,
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))
            SegmentedStepIndicator(currentStep = 1, totalSteps = 6)
            Spacer(Modifier.weight(1f))

            // Animated content block
            Column(
                modifier = Modifier
                    .offset(y = contentOffsetY)
                    .alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Eye icon → ties directly to the IRIS (iris of an eye) brand name
                // UNTESTED — verify PhIcons.Regular.Eye exists in the Phosphor version used
                OnboardingIconContainer(
                    icon          = PhIcons.Regular.Eye,
                    containerSize = 120.dp,
                    iconSize      = 50.dp
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text      = "Merhaba!",
                    style     = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text      = "Ben IRIS, senin kişisel yapay zeka asistanın.",
                    style     = MaterialTheme.typography.bodyLarge,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Feature highlights — 3 small info rows
                // UNTESTED — verify PhIcons.Regular.Lightning and PhIcons.Regular.Lock
                WelcomeFeatureRow(
                    icon = PhIcons.Regular.Microphone,
                    text = "Sesli komutlarla çalışır"
                )
                Spacer(Modifier.height(8.dp))
                WelcomeFeatureRow(
                    icon = PhIcons.Regular.Lightning,
                    text = "Araçlarla görevleri tamamlar"
                )
                Spacer(Modifier.height(8.dp))
                WelcomeFeatureRow(
                    icon = PhIcons.Regular.Lock,
                    text = "Veriler cihazında kalır, bulut yok"
                )

                Spacer(Modifier.height(28.dp))

                OutlinedTextField(
                    value          = userName,
                    onValueChange  = onUserNameChange,
                    label          = { Text("Seni nasıl çağırayım?") },
                    singleLine     = true,
                    leadingIcon    = {
                        Icon(
                            imageVector        = PhIcons.Regular.User,
                            contentDescription = null,
                            tint               = IrisTheme.colors.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = IrisTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedLabelColor    = IrisTheme.colors.primary,
                        cursorColor          = IrisTheme.colors.primary,
                        focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor   = MaterialTheme.colorScheme.onBackground
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            Spacer(Modifier.weight(1f))

            IrisButtonPrimary(
                text     = "Başla",
                onClick  = onNext,
                enabled  = userName.isNotBlank(),
                icon     = PhIcons.Filled.ArrowRightFill,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha)
                    .padding(bottom = 48.dp)
            )
        }
    }
}

// ─── Private composable: single feature highlight row ───────────────────────

@Composable
private fun WelcomeFeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(17.dp),
                tint               = IrisTheme.colors.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}