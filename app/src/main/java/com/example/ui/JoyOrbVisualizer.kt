package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.JoyUiState
import com.example.ui.theme.JoyCyan
import com.example.ui.theme.JoyElectricBlue
import com.example.ui.theme.JoyNeonGreen
import com.example.ui.theme.JoyVoidBlack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JoyOrbVisualizer(
    state: JoyUiState,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbInfiniteTransition")

    // Idle: Slow subtle breathing glow
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingScale"
    )

    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingAlpha"
    )

    // Thinking/Processing: Pulsing rotating neon ring
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulsePhase"
    )

    // Dynamic smoothing for audio amplitude
    val smoothAudioLevel = remember { Animatable(0f) }
    LaunchedEffect(audioLevel) {
        smoothAudioLevel.animateTo(
            targetValue = audioLevel.coerceIn(0f, 1f),
            animationSpec = tween(60, easing = FastOutSlowInEasing)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(280.dp)
            .testTag("joy_orb_visualizer")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.28f

            when (state) {
                JoyUiState.IDLE -> {
                    // 1. Slow subtle breathing glow
                    val glowRadius = baseRadius * breathingScale * 1.5f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JoyCyan.copy(alpha = 0.45f * breathingAlpha),
                                JoyElectricBlue.copy(alpha = 0.20f * breathingAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = center
                    )

                    // Outer thin decorative ring
                    drawCircle(
                        color = JoyCyan.copy(alpha = 0.3f * breathingAlpha),
                        radius = baseRadius * breathingScale,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Solid Inner Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JoyCyan.copy(alpha = 0.9f),
                                JoyElectricBlue,
                                JoyVoidBlack
                            ),
                            center = center,
                            radius = baseRadius * 0.9f
                        ),
                        radius = baseRadius * 0.85f,
                        center = center
                    )
                }

                JoyUiState.LISTENING -> {
                    // 2. Active listening waveform responding to microphone input frequency
                    val reactiveRadius = baseRadius * (1.0f + smoothAudioLevel.value * 0.75f)

                    // Pulsing sound energy aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JoyNeonGreen.copy(alpha = 0.5f),
                                JoyCyan.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = reactiveRadius * 1.6f
                        ),
                        radius = reactiveRadius * 1.6f,
                        center = center
                    )

                    // Active listening waveform bars radiating outwards
                    val barCount = 36
                    val angleStep = (2 * PI / barCount).toFloat()
                    for (i in 0 until barCount) {
                        val angle = i * angleStep + ringRotation * 0.02f
                        val waveOffset = sin(i * 0.8f + pulsePhase).coerceAtLeast(0.1f)
                        val barHeight = (14.dp.toPx() + (smoothAudioLevel.value * 50.dp.toPx() * waveOffset))

                        val startX = center.x + (baseRadius * 0.95f) * cos(angle)
                        val startY = center.y + (baseRadius * 0.95f) * sin(angle)
                        val endX = center.x + (baseRadius * 0.95f + barHeight) * cos(angle)
                        val endY = center.y + (baseRadius * 0.95f + barHeight) * sin(angle)

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(JoyCyan, JoyNeonGreen),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY)
                            ),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Core responsive inner orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JoyCyan,
                                JoyNeonGreen.copy(alpha = 0.8f),
                                JoyVoidBlack
                            ),
                            center = center,
                            radius = baseRadius * 0.85f
                        ),
                        radius = baseRadius * 0.82f,
                        center = center
                    )
                }

                JoyUiState.THINKING -> {
                    // 3. Pulsing neon ring
                    val pulseScale = 1.0f + 0.15f * sin(pulsePhase)

                    // Expanding outer neon halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JoyCyan.copy(alpha = 0.4f),
                                JoyElectricBlue.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * pulseScale * 1.4f
                        ),
                        radius = baseRadius * pulseScale * 1.4f,
                        center = center
                    )

                    // Rotating segmented neon rings
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(JoyCyan, JoyElectricBlue, Color.Transparent, JoyCyan),
                            center = center
                        ),
                        startAngle = ringRotation,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 1.15f, center.y - baseRadius * 1.15f),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 2.3f, baseRadius * 2.3f),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(JoyElectricBlue, JoyCyan, Color.Transparent, JoyElectricBlue),
                            center = center
                        ),
                        startAngle = -ringRotation * 1.5f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 0.95f, center.y - baseRadius * 0.95f),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 1.9f, baseRadius * 1.9f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Inner Core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JoyElectricBlue,
                                JoyCyan.copy(alpha = 0.8f),
                                JoyVoidBlack
                            ),
                            center = center,
                            radius = baseRadius * 0.75f
                        ),
                        radius = baseRadius * 0.75f,
                        center = center
                    )
                }

                JoyUiState.SPEAKING -> {
                    // 4. Dynamic audio wave matching Joy's output stream
                    val waveAmplitude = 20.dp.toPx()
                    val waveRadius = baseRadius * 1.35f

                    // Outer harmonic ripples
                    for (layer in 1..3) {
                        val layerAlpha = (0.6f / layer)
                        val layerRadius = baseRadius * (1.0f + layer * 0.18f + 0.1f * sin(pulsePhase * layer))
                        drawCircle(
                            color = JoyCyan.copy(alpha = layerAlpha),
                            radius = layerRadius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Rotating radiant energy crests
                    val crests = 28
                    val crestStep = (2 * PI / crests).toFloat()
                    for (i in 0 until crests) {
                        val angle = i * crestStep + ringRotation * 0.05f
                        val wave = sin(i * 1.2f + pulsePhase * 2f)
                        val currentHeight = waveAmplitude * (0.5f + 0.5f * wave)

                        val startX = center.x + (baseRadius * 0.88f) * cos(angle)
                        val startY = center.y + (baseRadius * 0.88f) * sin(angle)
                        val endX = center.x + (baseRadius * 0.88f + currentHeight) * cos(angle)
                        val endY = center.y + (baseRadius * 0.88f + currentHeight) * sin(angle)

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(JoyElectricBlue, JoyCyan),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY)
                            ),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Inner Glowing Core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                JoyCyan,
                                JoyElectricBlue,
                                JoyVoidBlack
                            ),
                            center = center,
                            radius = baseRadius * 0.8f
                        ),
                        radius = baseRadius * 0.8f,
                        center = center
                    )
                }
            }
        }

        // Center Icon Overlay
        val icon = when (state) {
            JoyUiState.IDLE -> Icons.Default.Mic
            JoyUiState.LISTENING -> Icons.Default.GraphicEq
            JoyUiState.THINKING -> Icons.Default.Psychology
            JoyUiState.SPEAKING -> Icons.Default.VolumeUp
        }

        val iconTint = when (state) {
            JoyUiState.IDLE -> JoyCyan
            JoyUiState.LISTENING -> JoyNeonGreen
            JoyUiState.THINKING -> JoyCyan
            JoyUiState.SPEAKING -> Color.White
        }

        Icon(
            imageVector = icon,
            contentDescription = "Joy Assistant Orb State: $state",
            tint = iconTint,
            modifier = Modifier.size(46.dp)
        )
    }
}
