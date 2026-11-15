package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.Path
import com.example.model.AssistantEmotion
import com.example.model.VoiceState
import com.example.ui.theme.SiriChampagneGold
import com.example.ui.theme.SiriCyan
import com.example.ui.theme.SiriDeepGold
import com.example.ui.theme.SiriGold
import com.example.ui.theme.SiriGoldWhite
import com.example.ui.theme.SiriLightGold
import com.example.ui.theme.SiriMagenta
import com.example.ui.theme.SiriNeonBlue
import com.example.ui.theme.SiriViolet
import com.example.ui.theme.SiriWarmGold
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Emotionally expressive 3D multi-layered Siri voice orb with live holographic facial expressions.
 * Preserves the obsidian-gold volumetric sphere, orbital rings, and resonance ripples while
 * adapting eye, brow, mouth, and aura expressions to conversation state & emotion.
 */
@Composable
fun SiriOrb(
    voiceState: VoiceState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    orbSize: Dp = 240.dp,
    reducedMotion: Boolean = false,
    emotion: AssistantEmotion = AssistantEmotion.NEUTRAL,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siri_orb_3d_transition")

    // Emotion + state-based animation speed
    val emotionSpeedFactor = when (emotion) {
        AssistantEmotion.EXCITED -> 0.72f
        AssistantEmotion.SURPRISED -> 0.80f
        AssistantEmotion.HAPPY -> 0.88f
        AssistantEmotion.CURIOUS -> 0.92f
        AssistantEmotion.SAD -> 1.38f
        AssistantEmotion.SERIOUS -> 1.18f
        AssistantEmotion.NEUTRAL -> 1.0f
    }
    val baseRotationSpeed = when (voiceState) {
        VoiceState.THINKING -> 2200
        VoiceState.SPEAKING -> 4200
        VoiceState.LISTENING -> 3200
        VoiceState.ERROR -> 9000
        VoiceState.IDLE -> 7500
    }
    val rotationSpeed = (baseRotationSpeed * emotionSpeedFactor).toInt().coerceIn(1400, 12000)

    // 1. Continuous smooth rotation of the internal plasma core
    val coreRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coreRotation"
    )

    // 2. Secondary counter-rotating gyroscopic angle
    val gyroAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (rotationSpeed * 1.45).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gyroAngle"
    )

    // 3. Gentle organic breathing cycle
    val breathScale by infiniteTransition.animateFloat(
        initialValue = if (emotion == AssistantEmotion.EXCITED) 0.94f else 0.96f,
        targetValue = if (emotion == AssistantEmotion.EXCITED) 1.06f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    voiceState == VoiceState.THINKING -> 1150
                    emotion == AssistantEmotion.EXCITED -> 1100
                    emotion == AssistantEmotion.SAD -> 3100
                    else -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // 4. Wave vibration phase for speaking & listening resonance
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.SPEAKING) 780 else 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 5. Particle orbital drift phase
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    // 6. Natural organic blinking phase (brief dip near end of 3.6s cycle)
    val blinkCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blinkCycle"
    )
    val blinkOpenness = if (reducedMotion) {
        1f
    } else {
        when {
            blinkCycle in 0.93f..0.96f -> (1f - (blinkCycle - 0.93f) / 0.03f).coerceIn(0.08f, 1f)
            blinkCycle > 0.96f -> ((blinkCycle - 0.96f) / 0.04f).coerceIn(0.08f, 1f)
            else -> 1f
        }
    }

    // 7. Smooth animated responsiveness to state changes & audio levels
    val smoothAudio by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "smoothAudio"
    )

    val stateEnergy by animateFloatAsState(
        targetValue = when (voiceState) {
            VoiceState.LISTENING -> 1.0f
            VoiceState.THINKING -> 1.15f
            VoiceState.SPEAKING -> 1.08f
            VoiceState.ERROR -> 0.75f
            VoiceState.IDLE -> 0.88f
        } * when (emotion) {
            AssistantEmotion.EXCITED -> 1.05f
            AssistantEmotion.SAD -> 0.94f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "stateEnergy"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(orbSize)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("siri_voice_orb")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.72f

            val audioExpansion = if (voiceState == VoiceState.LISTENING) {
                smoothAudio * 0.30f
            } else if (voiceState == VoiceState.SPEAKING) {
                0.08f * sin(wavePhase * 2f) + (smoothAudio * 0.14f)
            } else {
                0f
            }

            val effectiveScale = if (reducedMotion) 1.0f else (breathScale + audioExpansion)
            val currentRadius = baseRadius * effectiveScale * (stateEnergy * 0.98f)
            val effectiveCoreRotation = if (reducedMotion) 25f else coreRotation
            val effectiveGyroAngle = if (reducedMotion) 45f else gyroAngle
            val effectiveWavePhase = if (reducedMotion) 0f else wavePhase

            // Dynamic radiant palette guided by state & emotional tone
            val (cPrimary, cSecondary, cTertiary, cAccent) = when {
                voiceState == VoiceState.ERROR -> listOf(
                    Color(0xFFFF3D00),
                    Color(0xFFFF6D00),
                    SiriWarmGold,
                    Color(0xFFFFE0B2)
                )
                emotion == AssistantEmotion.SAD -> listOf(
                    Color(0xFF4FC3F7),
                    SiriNeonBlue,
                    SiriViolet,
                    Color(0xFFE1F5FE)
                )
                emotion == AssistantEmotion.EXCITED -> listOf(
                    SiriLightGold,
                    SiriMagenta,
                    SiriGold,
                    SiriGoldWhite
                )
                emotion == AssistantEmotion.SURPRISED -> listOf(
                    SiriCyan,
                    SiriLightGold,
                    SiriViolet,
                    SiriGoldWhite
                )
                emotion == AssistantEmotion.CURIOUS -> listOf(
                    SiriCyan,
                    SiriGold,
                    SiriViolet,
                    SiriChampagneGold
                )
                emotion == AssistantEmotion.HAPPY -> listOf(
                    SiriLightGold,
                    SiriGold,
                    SiriWarmGold,
                    SiriGoldWhite
                )
                emotion == AssistantEmotion.SERIOUS -> listOf(
                    SiriDeepGold,
                    Color(0xFFD97706),
                    SiriNeonBlue,
                    SiriChampagneGold
                )
                voiceState == VoiceState.LISTENING -> listOf(
                    SiriLightGold,
                    SiriGold,
                    SiriCyan,
                    SiriGoldWhite
                )
                else -> listOf(
                    SiriGold,
                    SiriWarmGold,
                    SiriDeepGold,
                    SiriChampagneGold
                )
            }

            // LAYER 1: Deep Volumetric Atmospheric Bloom
            val bloomRadius = currentRadius * (1.65f + (smoothAudio * 0.25f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cPrimary.copy(alpha = 0.36f),
                        cSecondary.copy(alpha = 0.18f),
                        cTertiary.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = bloomRadius
                ),
                radius = bloomRadius,
                center = center
            )

            val innerHaloRadius = currentRadius * 1.28f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cSecondary.copy(alpha = 0.48f),
                        cTertiary.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerHaloRadius
                ),
                radius = innerHaloRadius,
                center = center
            )

            // LAYER 2: Floating 3D Micro-Particles
            if (!reducedMotion) {
                drawFloatingParticles(
                    center = center,
                    baseRadius = currentRadius,
                    phase = particlePhase,
                    voiceState = voiceState,
                    colorA = cPrimary,
                    colorB = cTertiary
                )
            }

            // LAYER 3: 3D Gyroscopic Orbital Rings
            drawGyroscopicRings(
                center = center,
                radius = currentRadius,
                coreAngle = effectiveCoreRotation,
                gyroAngle = effectiveGyroAngle,
                wavePhase = effectiveWavePhase,
                smoothAudio = smoothAudio,
                voiceState = voiceState,
                cPrimary = cPrimary,
                cSecondary = cSecondary,
                cTertiary = cTertiary
            )

            // LAYER 4: Deep Spherical Core & Fluid Plasma
            drawVolumetricCore(
                center = center,
                radius = currentRadius,
                rotation = effectiveCoreRotation,
                wavePhase = effectiveWavePhase,
                voiceState = voiceState,
                cPrimary = cPrimary,
                cSecondary = cSecondary,
                cTertiary = cTertiary,
                cAccent = cAccent
            )

            // LAYER 4B: Emotional Holographic Facial Expression (Eyes, Brows, Animated Mouth)
            drawOrbExpression(
                center = center,
                radius = currentRadius,
                voiceState = voiceState,
                emotion = emotion,
                wavePhase = effectiveWavePhase,
                smoothAudio = smoothAudio,
                blinkOpenness = blinkOpenness,
                faceColor = Color.White,
                glowColor = cAccent
            )

            // LAYER 5: Optical Fresnel Rim Lighting
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        cPrimary.copy(alpha = 0.90f),
                        cSecondary.copy(alpha = 0.75f),
                        cTertiary.copy(alpha = 0.85f),
                        cAccent.copy(alpha = 0.65f),
                        cPrimary.copy(alpha = 0.90f)
                    ),
                    center = center
                ),
                radius = currentRadius,
                center = center,
                style = Stroke(width = 2.4.dp.toPx())
            )

            // LAYER 6: Curved 3D Specular Reflections
            drawGlassReflections(
                center = center,
                radius = currentRadius,
                cAccent = cAccent
            )
        }
    }
}

private fun DrawScope.drawOrbExpression(
    center: Offset,
    radius: Float,
    voiceState: VoiceState,
    emotion: AssistantEmotion,
    wavePhase: Float,
    smoothAudio: Float,
    blinkOpenness: Float,
    faceColor: Color,
    glowColor: Color
) {
    val strokeW = (radius * 0.055f).coerceIn(2.0f, 5.5f)
    val eyeSpacingX = radius * 0.29f
    val eyeBaseY = center.y - radius * 0.11f

    // Thinking horizontal gaze shift
    val gazeShiftX = if (voiceState == VoiceState.THINKING) {
        radius * 0.08f * sin(wavePhase * 0.8f)
    } else {
        0f
    }
    val leftEyeCenter = Offset(center.x - eyeSpacingX + gazeShiftX, eyeBaseY)
    val rightEyeCenter = Offset(center.x + eyeSpacingX + gazeShiftX, eyeBaseY)

    // Soft dark visor backing behind facial expression so glowing features stay crystal clear
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xAA080D18),
                Color(0x55080D18),
                Color.Transparent
            ),
            center = center,
            radius = radius * 0.74f
        ),
        radius = radius * 0.74f,
        center = center
    )

    // 1. Expressive Eyebrows
    val browYOffset = when (emotion) {
        AssistantEmotion.SURPRISED -> -radius * 0.21f
        AssistantEmotion.CURIOUS -> -radius * 0.17f
        AssistantEmotion.EXCITED -> -radius * 0.18f
        AssistantEmotion.SERIOUS -> -radius * 0.12f
        else -> -radius * 0.15f
    }
    val leftBrowTilt = when (emotion) {
        AssistantEmotion.SAD -> -0.10f * radius
        AssistantEmotion.SERIOUS -> 0.08f * radius
        AssistantEmotion.CURIOUS -> -0.06f * radius
        else -> 0f
    }
    val rightBrowTilt = when (emotion) {
        AssistantEmotion.SAD -> -0.10f * radius
        AssistantEmotion.SERIOUS -> 0.08f * radius
        AssistantEmotion.CURIOUS -> 0.05f * radius
        else -> 0f
    }
    val browHalfW = radius * 0.12f
    if (emotion != AssistantEmotion.NEUTRAL || voiceState == VoiceState.THINKING) {
        val leftInnerY = leftEyeCenter.y + browYOffset + leftBrowTilt
        val leftOuterY = leftEyeCenter.y + browYOffset - leftBrowTilt
        drawLine(
            color = glowColor.copy(alpha = 0.85f),
            start = Offset(leftEyeCenter.x - browHalfW, leftOuterY),
            end = Offset(leftEyeCenter.x + browHalfW, leftInnerY),
            strokeWidth = strokeW * 0.75f,
            cap = StrokeCap.Round
        )
        val rightInnerY = rightEyeCenter.y + browYOffset + rightBrowTilt
        val rightOuterY = rightEyeCenter.y + browYOffset - rightBrowTilt
        drawLine(
            color = glowColor.copy(alpha = 0.85f),
            start = Offset(rightEyeCenter.x - browHalfW, rightInnerY),
            end = Offset(rightEyeCenter.x + browHalfW, rightOuterY),
            strokeWidth = strokeW * 0.75f,
            cap = StrokeCap.Round
        )
    }

    // 2. Expressive Eyes with natural blink
    val eyeRadiusX = radius * when (emotion) {
        AssistantEmotion.SURPRISED -> 0.115f
        AssistantEmotion.EXCITED -> 0.105f
        AssistantEmotion.SERIOUS -> 0.095f
        else -> 0.095f
    }
    val baseEyeScaleY = when {
        voiceState == VoiceState.LISTENING -> 1.25f
        emotion == AssistantEmotion.SURPRISED -> 1.40f
        emotion == AssistantEmotion.SERIOUS -> 0.76f
        emotion == AssistantEmotion.SAD -> 0.85f
        else -> 1.12f
    }

    if (emotion == AssistantEmotion.HAPPY || emotion == AssistantEmotion.EXCITED) {
        // Warm cheerful upward crescent eyes ^ ^ (flashes open momentarily for excited)
        for (eyeC in listOf(leftEyeCenter, rightEyeCenter)) {
            if (emotion == AssistantEmotion.EXCITED && blinkOpenness > 0.4f) {
                val eyeH = eyeRadiusX * 1.15f * blinkOpenness
                drawOval(
                    color = faceColor.copy(alpha = 0.95f),
                    topLeft = Offset(eyeC.x - eyeRadiusX * 0.85f, eyeC.y - eyeH),
                    size = Size(eyeRadiusX * 1.7f, eyeH * 2f)
                )
            } else {
                val arcRectTopLeft = Offset(eyeC.x - eyeRadiusX, eyeC.y - eyeRadiusX * 0.55f)
                drawArc(
                    color = faceColor.copy(alpha = 0.95f),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = arcRectTopLeft,
                    size = Size(eyeRadiusX * 2f, eyeRadiusX * 1.3f),
                    style = Stroke(width = strokeW * 1.05f, cap = StrokeCap.Round)
                )
            }
        }
    } else {
        listOf(leftEyeCenter to 1f, rightEyeCenter to if (emotion == AssistantEmotion.CURIOUS) 1.18f else 1f)
            .forEach { (eyeC, curScale) ->
                val eyeH = max(strokeW * 0.65f, eyeRadiusX * baseEyeScaleY * curScale * blinkOpenness)
                // Outer luminous halo
                drawOval(
                    color = glowColor.copy(alpha = 0.35f),
                    topLeft = Offset(eyeC.x - eyeRadiusX * 1.25f, eyeC.y - eyeH * 1.25f),
                    size = Size(eyeRadiusX * 2.5f, eyeH * 2.5f)
                )
                drawOval(
                    color = faceColor.copy(alpha = 0.95f),
                    topLeft = Offset(eyeC.x - eyeRadiusX, eyeC.y - eyeH),
                    size = Size(eyeRadiusX * 2f, eyeH * 2f)
                )
            }
    }

    // 3. Expressive Mouth (animated with speaking audio + emotion)
    val mouthY = center.y + radius * 0.21f
    val mouthHalfW = radius * when (emotion) {
        AssistantEmotion.EXCITED -> 0.26f
        AssistantEmotion.HAPPY -> 0.23f
        AssistantEmotion.SURPRISED -> 0.11f
        AssistantEmotion.SAD -> 0.18f
        else -> 0.20f
    }

    val isActivelySpeaking = voiceState == VoiceState.SPEAKING
    val speakOpen = if (isActivelySpeaking) {
        (radius * (0.045f + 0.08f * kotlin.math.abs(sin(wavePhase * 1.5f)) + smoothAudio * 0.09f))
    } else {
        0f
    }

    when {
        emotion == AssistantEmotion.SURPRISED -> {
            val openR = max(radius * 0.09f, speakOpen * 1.1f)
            drawOval(
                color = glowColor.copy(alpha = 0.35f),
                topLeft = Offset(center.x - mouthHalfW * 1.3f, mouthY - openR * 1.3f),
                size = Size(mouthHalfW * 2.6f, openR * 2.6f)
            )
            drawOval(
                color = faceColor.copy(alpha = 0.95f),
                topLeft = Offset(center.x - mouthHalfW, mouthY - openR),
                size = Size(mouthHalfW * 2f, openR * 2f),
                style = Stroke(width = strokeW)
            )
        }
        isActivelySpeaking -> {
            // Dynamic talking mouth curved by underlying emotion
            val curveFactor = when (emotion) {
                AssistantEmotion.HAPPY, AssistantEmotion.EXCITED -> 0.14f * radius
                AssistantEmotion.SAD -> -0.07f * radius
                AssistantEmotion.SERIOUS -> 0.01f * radius
                else -> 0.07f * radius
            }
            val topPath = Path().apply {
                moveTo(center.x - mouthHalfW, mouthY)
                quadraticTo(center.x, mouthY - speakOpen * 0.55f + curveFactor * 0.2f, center.x + mouthHalfW, mouthY)
            }
            val bottomPath = Path().apply {
                moveTo(center.x - mouthHalfW, mouthY)
                quadraticTo(center.x, mouthY + speakOpen + curveFactor, center.x + mouthHalfW, mouthY)
            }
            drawPath(topPath, color = faceColor.copy(alpha = 0.92f), style = Stroke(width = strokeW, cap = StrokeCap.Round))
            drawPath(bottomPath, color = faceColor.copy(alpha = 0.95f), style = Stroke(width = strokeW, cap = StrokeCap.Round))
        }
        else -> {
            val path = Path().apply {
                moveTo(center.x - mouthHalfW, mouthY)
                when (emotion) {
                    AssistantEmotion.HAPPY, AssistantEmotion.EXCITED -> {
                        val depth = if (emotion == AssistantEmotion.EXCITED) 0.19f else 0.14f
                        quadraticTo(center.x, mouthY + radius * depth, center.x + mouthHalfW, mouthY)
                    }
                    AssistantEmotion.SAD -> {
                        quadraticTo(center.x, mouthY - radius * 0.11f, center.x + mouthHalfW, mouthY + radius * 0.02f)
                    }
                    AssistantEmotion.CURIOUS -> {
                        quadraticTo(center.x + radius * 0.06f, mouthY + radius * 0.06f, center.x + mouthHalfW, mouthY - radius * 0.04f)
                    }
                    AssistantEmotion.SERIOUS -> {
                        lineTo(center.x + mouthHalfW, mouthY)
                    }
                    AssistantEmotion.NEUTRAL -> {
                        quadraticTo(center.x, mouthY + radius * 0.06f, center.x + mouthHalfW, mouthY)
                    }
                }
            }
            drawPath(
                path = path,
                color = faceColor.copy(alpha = 0.92f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Draws 12 floating micro-particles orbiting in 3D parallax
 */
private fun DrawScope.drawFloatingParticles(
    center: Offset,
    baseRadius: Float,
    phase: Float,
    voiceState: VoiceState,
    colorA: Color,
    colorB: Color
) {
    val particleCount = if (voiceState == VoiceState.ERROR) 6 else 12
    for (i in 0 until particleCount) {
        val angleOffset = (i * (2 * Math.PI / particleCount)).toFloat()
        val pAngle = phase * (if (i % 2 == 0) 1f else -0.8f) + angleOffset

        // Elliptical 3D tilt orbit
        val distanceScale = 1.15f + (0.35f * sin((pAngle * 2) + i))
        val orbitDist = baseRadius * distanceScale

        val px = center.x + (orbitDist * cos(pAngle))
        val py = center.y + (orbitDist * sin(pAngle) * 0.72f) // tilted perspective

        val pRadius = (1.6f + (1.2f * sin(pAngle + i))).dp.toPx()
        val pAlpha = (0.35f + (0.45f * sin(pAngle))).coerceIn(0.1f, 0.85f)
        val pColor = if (i % 2 == 0) colorA else colorB

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = pAlpha),
                    pColor.copy(alpha = pAlpha * 0.6f),
                    Color.Transparent
                ),
                center = Offset(px, py),
                radius = pRadius * 2.2f
            ),
            radius = pRadius * 1.8f,
            center = Offset(px, py)
        )
    }
}

/**
 * Draws 3D gyroscopic tilted orbital rings projected into perspective
 */
private fun DrawScope.drawGyroscopicRings(
    center: Offset,
    radius: Float,
    coreAngle: Float,
    gyroAngle: Float,
    wavePhase: Float,
    smoothAudio: Float,
    voiceState: VoiceState,
    cPrimary: Color,
    cSecondary: Color,
    cTertiary: Color
) {
    // Ring 1: Equatorial perspective ring tilted at -28 degrees
    rotate(degrees = -28f + (5f * sin(wavePhase)), pivot = center) {
        val ring1Width = radius * 2.30f
        val ring1Height = radius * 0.92f
        drawOval(
            brush = Brush.sweepGradient(
                colors = listOf(
                    cPrimary.copy(alpha = 0.85f),
                    cSecondary.copy(alpha = 0.20f),
                    cTertiary.copy(alpha = 0.75f),
                    cPrimary.copy(alpha = 0.15f),
                    cPrimary.copy(alpha = 0.85f)
                ),
                center = center
            ),
            topLeft = Offset(center.x - ring1Width / 2f, center.y - ring1Height / 2f),
            size = Size(ring1Width, ring1Height),
            style = Stroke(
                width = (1.8f + (smoothAudio * 2.2f)).dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }

    // Ring 2: Polar orbital ring tilted at +46 degrees (counter-rotating)
    rotate(degrees = 46f + (coreAngle * 0.15f), pivot = center) {
        val ring2Width = radius * 2.10f
        val ring2Height = radius * 0.76f
        drawOval(
            brush = Brush.sweepGradient(
                colors = listOf(
                    cTertiary.copy(alpha = 0.80f),
                    cPrimary.copy(alpha = 0.25f),
                    cSecondary.copy(alpha = 0.85f),
                    cTertiary.copy(alpha = 0.15f),
                    cTertiary.copy(alpha = 0.80f)
                ),
                center = center
            ),
            topLeft = Offset(center.x - ring2Width / 2f, center.y - ring2Height / 2f),
            size = Size(ring2Width, ring2Height),
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }

    // Ring 3: Audio-reactive outward resonance wave ring (expands in listening & speaking)
    if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
        val reactivePulse = (smoothAudio * 18.dp.toPx()) + (4.dp.toPx() * sin(wavePhase * 2f))
        val waveRadius = radius * 1.18f + reactivePulse
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    cPrimary.copy(alpha = 0.55f),
                    cSecondary.copy(alpha = 0.25f),
                    cTertiary.copy(alpha = 0.55f),
                    Color.Transparent,
                    cPrimary.copy(alpha = 0.55f)
                ),
                center = center
            ),
            radius = waveRadius,
            center = center,
            style = Stroke(
                width = (1.5f + (smoothAudio * 2f)).dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Draws the dense 3D volumetric core sphere with multi-nodal internal plasma lights
 */
private fun DrawScope.drawVolumetricCore(
    center: Offset,
    radius: Float,
    rotation: Float,
    wavePhase: Float,
    voiceState: VoiceState,
    cPrimary: Color,
    cSecondary: Color,
    cTertiary: Color,
    cAccent: Color
) {
    // 1. Dark deep obsidian-gold background sphere base
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF261D0A),
                Color(0xFF160F04),
                Color(0xFF090602)
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )

    // 2. Swirling internal plasma nodes in 3D perspective
    val radAngle = Math.toRadians(rotation.toDouble())

    // Node A (Cyan / Primary)
    val nodeAOffset = Offset(
        x = center.x + (radius * 0.42f * cos(radAngle)).toFloat(),
        y = center.y + (radius * 0.42f * sin(radAngle)).toFloat()
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                cPrimary.copy(alpha = 0.85f),
                cPrimary.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = nodeAOffset,
            radius = radius * 0.85f
        ),
        radius = radius * 0.85f,
        center = nodeAOffset
    )

    // Node B (Secondary / Violet) orbiting 120 degrees offset
    val radAngleB = radAngle + (2 * Math.PI / 3)
    val nodeBOffset = Offset(
        x = center.x + (radius * 0.38f * cos(radAngleB)).toFloat(),
        y = center.y + (radius * 0.38f * sin(radAngleB)).toFloat()
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                cSecondary.copy(alpha = 0.80f),
                cSecondary.copy(alpha = 0.35f),
                Color.Transparent
            ),
            center = nodeBOffset,
            radius = radius * 0.80f
        ),
        radius = radius * 0.80f,
        center = nodeBOffset
    )

    // Node C (Tertiary / Magenta) orbiting 240 degrees offset
    val radAngleC = radAngle + (4 * Math.PI / 3)
    val nodeCOffset = Offset(
        x = center.x + (radius * 0.34f * cos(radAngleC)).toFloat(),
        y = center.y + (radius * 0.34f * sin(radAngleC)).toFloat()
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cTertiary.copy(alpha = 0.85f),
                cTertiary.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = nodeCOffset,
            radius = radius * 0.75f
        ),
        radius = radius * 0.75f,
        center = nodeCOffset
    )

    // 3. Central luminous energy core
    val coreGlowRadius = if (voiceState == VoiceState.THINKING) radius * 0.52f else radius * 0.38f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                cAccent.copy(alpha = 0.75f),
                cPrimary.copy(alpha = 0.35f),
                Color.Transparent
            ),
            center = center,
            radius = coreGlowRadius
        ),
        radius = coreGlowRadius,
        center = center
    )
}

/**
 * Draws curved glass reflections (caustics & highlights) on the sphere dome
 */
private fun DrawScope.drawGlassReflections(
    center: Offset,
    radius: Float,
    cAccent: Color
) {
    // 1. Primary curved specular highlight at top-left dome
    val highlightCenter = Offset(
        center.x - (radius * 0.35f),
        center.y - (radius * 0.38f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f),
                Color.White.copy(alpha = 0.35f),
                Color.Transparent
            ),
            center = highlightCenter,
            radius = radius * 0.42f
        ),
        radius = radius * 0.42f,
        center = highlightCenter
    )

    // 2. Secondary ambient bounce light at bottom-right rim
    val bounceCenter = Offset(
        center.x + (radius * 0.38f),
        center.y + (radius * 0.40f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cAccent.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = bounceCenter,
            radius = radius * 0.36f
        ),
        radius = radius * 0.36f,
        center = bounceCenter
    )
}
