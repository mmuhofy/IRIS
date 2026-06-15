package com.iris.assistant.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun IrisCoreAnimation(
    modifier    : Modifier      = Modifier,
    state       : IrisCoreState = IrisCoreState.IDLE,
    amplitude   : Float         = 0f,
    ttsProgress : Float         = 0f,
    coreSize    : Dp            = Constants.IRIS_CORE_SIZE.dp
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val secondary   = IrisTheme.colors.secondary
    val error       = MaterialTheme.colorScheme.error

    val simAmplitude = if (state == IrisCoreState.LISTENING) amplitude
                       else if (state == IrisCoreState.SPEAKING) ttsProgress
                       else 0.02f

    var time by remember { mutableFloatStateOf(0f) }
    var pupilCurrent by remember { mutableFloatStateOf(0.18f) }

    val pupilTarget = when (state) {
        IrisCoreState.IDLE      -> 0.18f
        IrisCoreState.LISTENING -> 0.09f
        IrisCoreState.THINKING  -> 0.27f
        IrisCoreState.SPEAKING  -> 0.15f
    }

    LaunchedEffect(Unit) {
        val targetFps = 60L
        val frameDuration = 1000L / targetFps
        while (true) {
            delay(frameDuration)
            time += 0.02f
            pupilCurrent += (pupilTarget - pupilCurrent) * 0.1f
        }
    }

    Canvas(modifier = modifier.size(coreSize)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = size.minDimension
        val eyeRadius = minDim * 0.44f
        val spacing = minDim / 28f
        val gridSize = 15

        for (i in -gridSize..gridSize) {
            for (j in -gridSize..gridSize) {
                drawEyePoint(
                    cx, cy, eyeRadius, spacing, i, j,
                    pupilCurrent, time, simAmplitude, state,
                    primary, gradientEnd, secondary, error
                )
            }
        }

        val glowColor = when (state) {
            IrisCoreState.IDLE      -> primary
            IrisCoreState.LISTENING -> gradientEnd
            IrisCoreState.THINKING  -> error
            IrisCoreState.SPEAKING  -> secondary
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = 0.05f), glowColor.copy(alpha = 0.02f), Color.Transparent),
                center = Offset(cx, cy),
                radius = eyeRadius
            ),
            radius = eyeRadius,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawEyePoint(
    cx: Float, cy: Float, eyeRadius: Float, spacing: Float,
    i: Int, j: Int,
    pupilNorm: Float, time: Float, amplitude: Float,
    state: IrisCoreState,
    primary: Color, gradientEnd: Color, secondary: Color, error: Color
) {
    val ox = cx + i * spacing
    val oy = cy + j * spacing
    val dx = ox - cx
    val dy = oy - cy
    val dist = sqrt(dx * dx + dy * dy)
    if (dist > eyeRadius) return

    val normDist = (dist / eyeRadius).coerceAtMost(1f)
    val z = sqrt(1f - normDist * normDist)
    val lensDistortion = (1f - z) * 0.5f
    var fx = ox - dx * lensDistortion
    var fy = oy - dy * lensDistortion
    var cd = sqrt((fx - cx) * (fx - cx) + (fy - cy) * (fy - cy))

    val pupilRadius = pupilNorm * eyeRadius
    if (cd < pupilRadius) {
        val angle = atan2(fy - cy, fx - cx)
        fx = cx + cos(angle) * pupilRadius
        fy = cy + sin(angle) * pupilRadius
        cd = pupilRadius
    }

    var px = fx
    var py = fy
    var alpha: Float
    var pointColor: Color
    var pointSize = 1.2f * (z * 1.3f)

    when (state) {
        IrisCoreState.IDLE -> {
            val pulse = sin(cd * 0.05f - time * 2.5f) * 1.8f
            val angle = atan2(py - cy, px - cx)
            px += cos(angle) * pulse
            py += sin(angle) * pulse
            alpha = z * (0.15f + (1f - cd / eyeRadius) * 0.4f)
            pointColor = primary
        }
        IrisCoreState.LISTENING -> {
            val force = sin(cd * 0.07f - time * 14f) * (amplitude * 20f)
            val angle = atan2(py - cy, px - cx)
            px += cos(angle) * force * z
            py += sin(angle) * force * z
            alpha = z * (0.2f + (1f - cd / eyeRadius) * (0.4f + amplitude * 0.4f))
            pointColor = gradientEnd
            if (cd < pupilRadius + 8f) {
                pointSize += amplitude * 1.8f
            }
        }
        IrisCoreState.THINKING -> {
            val angle = atan2(py - cy, px - cx)
            val speed = (1f - (cd / eyeRadius)) * 2.2f
            px = cx + cos(angle + time * speed) * cd
            py = cy + sin(angle + time * speed) * cd
            alpha = z * (0.2f + (1f - cd / eyeRadius) * 0.5f)
            pointColor = error
        }
        IrisCoreState.SPEAKING -> {
            val simAmp = if (amplitude > 0.02f) amplitude else 0.3f + cos(time * 10f) * 0.25f
            val wave = sin(cd * 0.08f - time * 12f) * (simAmp * 16f)
            val angle = atan2(py - cy, px - cx)
            px += cos(angle) * wave * z
            py += sin(angle) * wave * z
            alpha = z * (0.15f + (1f - cd / eyeRadius) * 0.45f)
            pointColor = secondary
        }
    }

    drawRect(
        color = pointColor.copy(alpha = alpha.coerceIn(0f, 1f)),
        topLeft = Offset(px - pointSize / 2f, py - pointSize / 2f),
        size = androidx.compose.ui.geometry.Size(pointSize, pointSize)
    )
}
