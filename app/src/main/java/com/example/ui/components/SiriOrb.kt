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
import androidx.compose.ui.unit.dp
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
import kotlin.math.sin

/**
 * Premium, futuristic, ultra-smooth 3D multi-layered Siri voice orb.
 *
 * Visual hierarchy:
 * 1. Deep atmospheric volumetric diffuse glow
 * 2. Translucent floating micro-particles drifting in 3D parallax orbits
 * 3. Gyroscopic 3D-projected tilted orbital rings (equatorial, polar & reactive resonance)
 * 4. Dense 3D volumetric glass sphere with internal swirling plasma lights (Cyan, Blue, Violet, Magenta)
 * 5. Optical Fresnel rim lighting (inner edge refraction)
 * 6. Dual curved caustic specular reflections (primary curved glint + ambient bounce)
 * 7. Audio-reactive resonance ripples synchronized with microphone amplitude and speech output
 */
@Composable
fun SiriOrb(
    voiceState: VoiceState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siri_orb_3d_transition")

    // State-based animation parameters
    val rotationSpeed = when (voiceState) {
        VoiceState.THINKING -> 2200
        VoiceState.SPEAKING -> 4200
        VoiceState.LISTENING -> 3200
        VoiceState.ERROR -> 9000
        VoiceState.IDLE -> 7500
    }

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
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.THINKING) 1200 else 2400,
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
                durationMillis = if (voiceState == VoiceState.SPEAKING) 900 else 1600,
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

    // 6. Smooth animated responsiveness to state changes & audio levels
    val smoothAudio by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "smoothAudio"
    )

    val stateEnergy by animateFloatAsState(
        targetValue = when (voiceState) {
            VoiceState.LISTENING -> 1.0f
            VoiceState.THINKING -> 1.15f
            VoiceState.SPEAKING -> 1.08f
            VoiceState.ERROR -> 0.75f
            VoiceState.IDLE -> 0.85f
        },
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "stateEnergy"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(280.dp)
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

            // Dynamic scale combining breath, audio decibels, and state energy
            val audioExpansion = if (voiceState == VoiceState.LISTENING) {
                smoothAudio * 0.32f
            } else if (voiceState == VoiceState.SPEAKING) {
                0.08f * sin(wavePhase * 2f) + (smoothAudio * 0.15f)
            } else {
                0f
            }

            val currentRadius = baseRadius * (breathScale + audioExpansion) * (stateEnergy * 0.98f)

            // Dynamic radiant Golden palette based on state
            val (cPrimary, cSecondary, cTertiary, cAccent) = when (voiceState) {
                VoiceState.ERROR -> listOf(
                    Color(0xFFFF3D00),
                    Color(0xFFFF6D00),
                    SiriWarmGold,
                    Color(0xFFFFE0B2)
                )
                VoiceState.THINKING -> listOf(
                    SiriGold,
                    SiriWarmGold,
                    SiriDeepGold,
                    SiriGoldWhite
                )
                VoiceState.LISTENING -> listOf(
                    SiriLightGold,
                    SiriGold,
                    SiriWarmGold,
                    SiriGoldWhite
                )
                VoiceState.SPEAKING -> listOf(
                    SiriGold,
                    SiriDeepGold,
                    SiriWarmGold,
                    SiriChampagneGold
                )
                VoiceState.IDLE -> listOf(
                    SiriGold,
                    SiriWarmGold,
                    SiriDeepGold,
                    SiriChampagneGold
                )
            }

            // =================================================================
            // LAYER 1: Deep Volumetric Atmospheric Bloom (Multi-layered Glow)
            // =================================================================
            val bloomRadius = currentRadius * (1.65f + (smoothAudio * 0.25f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cPrimary.copy(alpha = 0.38f),
                        cSecondary.copy(alpha = 0.20f),
                        cTertiary.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = bloomRadius
                ),
                radius = bloomRadius,
                center = center
            )

            // Secondary closer halo
            val innerHaloRadius = currentRadius * 1.28f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cSecondary.copy(alpha = 0.50f),
                        cTertiary.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerHaloRadius
                ),
                radius = innerHaloRadius,
                center = center
            )

            // =================================================================
            // LAYER 2: Floating 3D Micro-Particles (Space/Holographic Depth)
            // =================================================================
            drawFloatingParticles(
                center = center,
                baseRadius = currentRadius,
                phase = particlePhase,
                voiceState = voiceState,
                colorA = cPrimary,
                colorB = cTertiary
            )

            // =================================================================
            // LAYER 3: 3D Gyroscopic Orbital Rings (Tilted Perspective Rings)
            // =================================================================
            drawGyroscopicRings(
                center = center,
                radius = currentRadius,
                coreAngle = coreRotation,
                gyroAngle = gyroAngle,
                wavePhase = wavePhase,
                smoothAudio = smoothAudio,
                voiceState = voiceState,
                cPrimary = cPrimary,
                cSecondary = cSecondary,
                cTertiary = cTertiary
            )

            // =================================================================
            // LAYER 4: Deep Spherical Core & Fluid Multi-Light Plasma
            // =================================================================
            drawVolumetricCore(
                center = center,
                radius = currentRadius,
                rotation = coreRotation,
                wavePhase = wavePhase,
                voiceState = voiceState,
                cPrimary = cPrimary,
                cSecondary = cSecondary,
                cTertiary = cTertiary,
                cAccent = cAccent
            )

            // =================================================================
            // LAYER 5: Optical Fresnel Rim Lighting (Refractive Glass Edge)
            // =================================================================
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

            // =================================================================
            // LAYER 6: Curved 3D Specular Reflections (Glass Caustics)
            // =================================================================
            drawGlassReflections(
                center = center,
                radius = currentRadius,
                cAccent = cAccent
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
