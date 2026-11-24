package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.model.AssistantState
import com.jarvis.assistant.ui.theme.JarvisColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisOrb(
    state: AssistantState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "jarvis_orb")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.THINKING, AssistantState.EXECUTING -> 2800
                    AssistantState.LISTENING, AssistantState.SPEAKING -> 4500
                    else -> 11000
                },
                easing = LinearEasing
            )
        ),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringColor = when (state) {
        AssistantState.LISTENING -> JarvisColors.CyanCore
        AssistantState.THINKING, AssistantState.EXECUTING -> JarvisColors.ElectricBlue
        AssistantState.SPEAKING -> Color(0xFF38BDF8)
        AssistantState.OFFLINE -> JarvisColors.StatusWarning
        AssistantState.ERROR -> JarvisColors.StatusError
        AssistantState.STANDBY -> JarvisColors.CyanCore.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier.size(228.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(228.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.31f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.48f,
                center = center
            )

            // Outer segmented arc
            drawArc(
                color = ringColor.copy(alpha = 0.75f),
                startAngle = rotationAngle,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = JarvisColors.ElectricBlue.copy(alpha = 0.5f),
                startAngle = -rotationAngle * 1.4f,
                sweepAngle = 140f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Waveform spikes driven by real mic RMS during LISTENING or subtle rhythm during SPEAKING
            val barCount = 48
            val dynamicAmp = when (state) {
                AssistantState.LISTENING -> (0.12f + rmsLevel * 0.88f)
                AssistantState.SPEAKING -> 0.45f * pulse
                AssistantState.THINKING -> 0.28f
                else -> 0.05f
            }

            for (i in 0 until barCount) {
                val angleRad = (i * (360f / barCount) + rotationAngle * 0.25f) * (PI / 180.0)
                val harmonic = sin(i * 0.7 + rotationAngle * 0.08).toFloat() * 0.5f + 0.5f
                val barLen = (4.dp.toPx() + 20.dp.toPx() * dynamicAmp * harmonic)
                val innerR = baseRadius + 10.dp.toPx()
                val outerR = innerR + barLen

                val start = Offset(
                    x = center.x + (innerR * cos(angleRad)).toFloat(),
                    y = center.y + (innerR * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    x = center.x + (outerR * cos(angleRad)).toFloat(),
                    y = center.y + (outerR * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = ringColor.copy(alpha = 0.85f),
                    start = start,
                    end = end,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Inner core circle
            drawCircle(
                color = JarvisColors.DeepNavy,
                radius = baseRadius * 0.88f,
                center = center
            )
            drawCircle(
                color = ringColor,
                radius = baseRadius * 0.88f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Text(
            text = "J.A.R.V.I.S",
            color = JarvisColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}
