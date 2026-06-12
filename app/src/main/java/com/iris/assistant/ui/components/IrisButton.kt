package com.iris.assistant.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.ColorError
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants

// Corner radius per design spec
private val ButtonCornerRadius = 16.dp

// ---------------------------------------------------------------------------
// IrisButton — Primary
// ---------------------------------------------------------------------------
@Composable
fun IrisButtonPrimary(
    text     : String,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier,
    enabled  : Boolean  = true,
    isLoading: Boolean  = false,
    icon     : ImageVector? = null
) {
    Button(
        onClick       = onClick,
        enabled       = enabled && !isLoading,
        modifier      = modifier.height(Constants.BUTTON_HEIGHT.dp),
        shape         = RoundedCornerShape(ButtonCornerRadius),
        colors        = ButtonDefaults.buttonColors(
            containerColor         = IrisTheme.colors.primary,
            contentColor           = ColorTextPrimary,
            disabledContainerColor = IrisTheme.colors.primary.copy(alpha = 0.38f),
            disabledContentColor   = ColorTextPrimary.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier  = Modifier.size(18.dp),
                color     = ColorTextPrimary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text)
        }
    }
}

// ---------------------------------------------------------------------------
// IrisButton — Secondary (outlined)
// ---------------------------------------------------------------------------
@Composable
fun IrisButtonSecondary(
    text    : String,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
    enabled : Boolean  = true,
    icon    : ImageVector? = null
) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier.height(Constants.BUTTON_HEIGHT.dp),
        shape    = RoundedCornerShape(ButtonCornerRadius),
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor         = IrisTheme.colors.primary,
            disabledContentColor = IrisTheme.colors.primary.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(18.dp),
                tint               = IrisTheme.colors.primary
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text)
    }
}

// ---------------------------------------------------------------------------
// IrisButton — Destructive
// ---------------------------------------------------------------------------
@Composable
fun IrisButtonDestructive(
    text    : String,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
    enabled : Boolean  = true
) {
    Button(
        onClick        = onClick,
        enabled        = enabled,
        modifier       = modifier.height(Constants.BUTTON_HEIGHT.dp),
        shape          = RoundedCornerShape(ButtonCornerRadius),
        colors         = ButtonDefaults.buttonColors(
            containerColor         = ColorError,
            contentColor           = Color.White,
            disabledContainerColor = ColorError.copy(alpha = 0.38f),
            disabledContentColor   = Color.White.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(text = text)
    }
}