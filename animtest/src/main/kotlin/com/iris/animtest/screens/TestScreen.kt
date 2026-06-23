package com.iris.animtest.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Colors {
    val A = Color(0xFF6C63FF)
    val B = Color(0xFF00BFA5)
    val C = Color(0xFFFF6B6B)
    val D = Color(0xFFFFA726)
}

@Composable
fun TestScreen(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)?,
    onPrev: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (onPrev != null) {
                Button(
                    onClick = onPrev,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "\u2190 Back",
                        fontSize = 18.sp,
                        color = Color.White,
                    )
                }
            }

            if (onNext != null) {
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Next \u2192",
                        fontSize = 18.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
