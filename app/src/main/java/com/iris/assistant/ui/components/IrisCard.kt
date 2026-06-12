package com.iris.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.ColorSurface

// Corner radius per design spec
private val CardCornerRadius = 18.dp

// ---------------------------------------------------------------------------
// IrisCard — base surface card, flat color + subtle shadow (no glassmorphism)
// ---------------------------------------------------------------------------
@Composable
fun IrisCard(
    modifier     : Modifier    = Modifier,
    elevation    : Dp          = 4.dp,
    innerPadding : Dp          = 16.dp,
    content      : @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(CardCornerRadius),
        colors    = CardDefaults.cardColors(containerColor = ColorSurface),
        elevation = CardDefaults.cardElevation(
            defaultElevation  = elevation,
            pressedElevation  = elevation / 2,
            focusedElevation  = elevation
        )
    ) {
        Column(
            modifier = Modifier.padding(innerPadding),
            content  = content
        )
    }
}

// ---------------------------------------------------------------------------
// IrisCardClickable — same as IrisCard but tappable
// ---------------------------------------------------------------------------
@Composable
fun IrisCardClickable(
    onClick     : () -> Unit,
    modifier    : Modifier = Modifier,
    elevation   : Dp       = 4.dp,
    innerPadding: Dp       = 16.dp,
    content     : @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = modifier,
        shape     = RoundedCornerShape(CardCornerRadius),
        colors    = CardDefaults.cardColors(containerColor = ColorSurface),
        elevation = CardDefaults.cardElevation(
            defaultElevation  = elevation,
            pressedElevation  = elevation / 2,
            focusedElevation  = elevation
        )
    ) {
        Column(
            modifier = Modifier.padding(innerPadding),
            content  = content
        )
    }
}