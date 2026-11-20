package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.Face6
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.NarratorGender
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature 3-layer harmonic acoustic oscilloscope strip with analog millimeter ticks
 * and subtle luminous resonance crests when synthesizing or playing audio.
 */
@Composable
fun AcousticRibbonVisualizer(
    isActive: Boolean,
    isSynthesizing: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "acoustic_ribbon")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSynthesizing) 1050 else if (isActive) 1500 else 4400,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_phase",
    )

    val amplitude by animateFloatAsState(
        targetValue = when {
            isSynthesizing -> 0.92f
            isActive -> 0.78f
            else -> 0.16f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "wave_amp",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val tickColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // Subtle studio ruler tick marks along bottom edge
        val tickCount = 36
        for (i in 0..tickCount) {
            val x = (i / tickCount.toFloat()) * w
            val major = i % 6 == 0
            val tickH = if (major) 7.dp.toPx() else 3.5.dp.toPx()
            drawLine(
                color = if (major) tickColor.copy(alpha = 0.9f) else tickColor.copy(alpha = 0.55f),
                start = Offset(x, h - tickH),
                end = Offset(x, h),
                strokeWidth = if (major) 1.2.dp.toPx() else 0.8.dp.toPx(),
            )
        }

        val steps = 84

        // Overtone shimmer wave (Warm Ochre)
        if (amplitude > 0.22f) {
            val pathOvertone = Path()
            for (i in 0..steps) {
                val norm = i / steps.toFloat()
                val x = norm * w
                val envelope = sin(norm * PI).toFloat()
                val y = centerY + cos(norm * 7.0f * PI - phase * 1.8f).toFloat() * (h * 0.22f) * amplitude * envelope
                if (i == 0) pathOvertone.moveTo(x, y) else pathOvertone.lineTo(x, y)
            }
            drawPath(
                path = pathOvertone,
                color = tertiaryColor.copy(alpha = (amplitude - 0.15f).coerceIn(0f, 0.4f)),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Secondary harmonic wave (Persian Turquoise)
        val pathSecondary = Path()
        for (i in 0..steps) {
            val norm = i / steps.toFloat()
            val x = norm * w
            val envelope = sin(norm * PI).toFloat()
            val y = centerY + sin(norm * 5.2f * PI + phase * 1.4f).toFloat() * (h * 0.34f) * amplitude * envelope
            if (i == 0) pathSecondary.moveTo(x, y) else pathSecondary.lineTo(x, y)
        }
        drawPath(
            path = pathSecondary,
            color = secondaryColor.copy(alpha = if (isActive || isSynthesizing) 0.62f else 0.25f),
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round),
        )

        // Primary harmonic wave (Vermilion gradient)
        val pathPrimary = Path()
        for (i in 0..steps) {
            val norm = i / steps.toFloat()
            val x = norm * w
            val envelope = sin(norm * PI).toFloat()
            val y = centerY + sin(norm * 3.5f * PI - phase).toFloat() * (h * 0.41f) * amplitude * envelope
            if (i == 0) pathPrimary.moveTo(x, y) else pathPrimary.lineTo(x, y)
        }
        drawPath(
            path = pathPrimary,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = if (isActive || isSynthesizing) 0.7f else 0.3f),
                    primaryColor.copy(alpha = if (isActive || isSynthesizing) 0.98f else 0.48f),
                    primaryColor.copy(alpha = if (isActive || isSynthesizing) 0.85f else 0.35f),
                )
            ),
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Living acoustic waveform with 60fps interpolated playhead, dynamic harmonic bar bounce
 * near the active playhead, gradient color sweep, and glowing playhead cursor bead.
 */
@Composable
fun InteractiveWaveformView(
    peaks: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)
    val bars = if (peaks.isEmpty()) List(42) { 0.24f } else peaks

    // Smooth out discrete AudioTrack progress updates into fluid 60fps playhead glide
    val smoothProgress by animateFloatAsState(
        targetValue = if (isPlaying) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "waveform_progress",
    )

    val liveEnergy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "waveform_energy",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val eqPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "eq_phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        val count = bars.size
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.48f).coerceAtLeast(2.5f)
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val normPos = (i + 0.5f) / count.toFloat()
            val baseAmp = bars[i].coerceIn(0.14f, 1f)

            // Localized acoustic vitality wave centered around the active playhead
            val distFromHead = abs(normPos - smoothProgress)
            val headProximity = (1f - (distFromHead / 0.18f)).coerceIn(0f, 1f)
            val rippleBoost = if (liveEnergy > 0.01f) {
                val osc = 0.5f + 0.5f * sin(eqPhase * 2f + i * 0.65f).toFloat()
                liveEnergy * (0.08f * osc + 0.34f * headProximity * (0.45f + 0.55f * osc))
            } else {
                0f
            }

            val finalAmp = (baseAmp + rippleBoost).coerceIn(0.14f, 1f)
            val barH = size.height * 0.84f * finalAmp
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val y = centerY - barH / 2f

            val barColor = when {
                liveEnergy > 0.05f && normPos <= smoothProgress -> {
                    val blend = (1f - (smoothProgress - normPos).coerceIn(0f, 0.45f) / 0.45f)
                    Color(
                        red = secondaryColor.red + (activeColor.red - secondaryColor.red) * blend,
                        green = secondaryColor.green + (activeColor.green - secondaryColor.green) * blend,
                        blue = secondaryColor.blue + (activeColor.blue - secondaryColor.blue) * blend,
                        alpha = 1f,
                    )
                }
                liveEnergy > 0.05f && headProximity > 0.5f -> {
                    activeColor.copy(alpha = 0.32f * headProximity * liveEnergy + 0.35f)
                }
                else -> inactiveColor
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }

        // Glowing playhead cursor line + bead
        if (liveEnergy > 0.05f) {
            val headX = smoothProgress * size.width
            drawLine(
                color = activeColor.copy(alpha = 0.22f * liveEnergy),
                start = Offset(headX, 2.dp.toPx()),
                end = Offset(headX, size.height - 2.dp.toPx()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = activeColor.copy(alpha = liveEnergy),
                start = Offset(headX, 3.dp.toPx()),
                end = Offset(headX, size.height - 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = activeColor.copy(alpha = liveEnergy),
                radius = 3.5.dp.toPx(),
                center = Offset(headX, centerY),
            )
        }
    }
}

/**
 * Smooth geometric morphing Play (▶) ↔ Pause (⏸) icon using a spring-interpolated Canvas path.
 * Both halves of the Play triangle seamlessly separate and morph into crisp rounded Pause bars
 * with a subtle elastic scale pulse during transition.
 */
@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "play_pause_morph",
    )

    val scalePulse = 1f - 0.08f * sin(morph.coerceIn(0f, 1f) * PI).toFloat()

    Canvas(
        modifier = modifier
            .size(22.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
    ) {
        val t = morph.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        fun lerp(a: Float, b: Float): Float = a + (b - a) * t
        fun pt(nx: Float, ny: Float): Offset {
            val sx = cx + (nx * w - cx) * scalePulse
            val sy = cy + (ny * h - cy) * scalePulse
            return Offset(sx, sy)
        }

        // Left half of Play triangle → Left Pause bar
        val leftPath = Path().apply {
            val p0 = pt(lerp(0.24f, 0.21f), lerp(0.16f, 0.18f))
            val p1 = pt(lerp(0.55f, 0.39f), lerp(0.33f, 0.18f))
            val p2 = pt(lerp(0.55f, 0.39f), lerp(0.67f, 0.82f))
            val p3 = pt(lerp(0.24f, 0.21f), lerp(0.84f, 0.82f))
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }

        // Right half of Play triangle (apex) → Right Pause bar
        val rightPath = Path().apply {
            val p0 = pt(lerp(0.535f, 0.61f), lerp(0.322f, 0.18f))
            val p1 = pt(lerp(0.84f, 0.79f), lerp(0.50f, 0.18f))
            val p2 = pt(lerp(0.84f, 0.79f), lerp(0.50f, 0.82f))
            val p3 = pt(lerp(0.535f, 0.61f), lerp(0.678f, 0.82f))
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }

        val roundedStroke = Stroke(
            width = (w * 0.085f).coerceAtLeast(1.5f),
            join = StrokeJoin.Round,
            cap = StrokeCap.Round,
        )

        drawPath(path = leftPath, color = tint, style = Fill)
        drawPath(path = leftPath, color = tint, style = roundedStroke)
        drawPath(path = rightPath, color = tint, style = Fill)
        drawPath(path = rightPath, color = tint, style = roundedStroke)
    }
}

/**
 * Tactile circular playback button combining a live expanding acoustic ripple halo
 * while playing, smooth color/scale spring transitions, and the morphing Play/Pause icon.
 */
@Composable
fun AnimatedPlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 50.dp,
    iconSize: Dp = 22.dp,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "playback_btn_bg",
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "playback_btn_scale",
    )

    val pulseTransition = rememberInfiniteTransition(label = "playback_halo")
    val pulseFraction by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo_fraction",
    )

    val haloColor = MaterialTheme.colorScheme.primary
    val outerSize = buttonSize + 14.dp

    Box(
        modifier = Modifier.size(outerSize),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val minR = (buttonSize.toPx() / 2f)
                val maxR = size.minDimension / 2f
                val r = minR + (maxR - minR) * pulseFraction
                val alpha = (1f - pulseFraction) * 0.42f
                drawCircle(
                    color = haloColor.copy(alpha = alpha),
                    radius = r,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = bgColor,
            shadowElevation = if (isPlaying) 6.dp else 2.dp,
            modifier = modifier
                .size(buttonSize)
                .scale(buttonScale),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedPlayPauseIcon(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.surface,
                    contentDescription = if (isPlaying) "توقف پخش" else "پخش صدا",
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * Tactile hardware-inspired circular microphone button with concentric acoustic ripples.
 */
@Composable
fun TactileRecordOrb(
    isRecording: Boolean,
    micLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isRecording) micLevel.coerceIn(0.15f, 1f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "orb_level",
    )
    val orbColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        label = "orb_color",
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val hairline = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier.size(136.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 42.dp.toPx()

            // Outer resting technical circle
            drawCircle(
                color = hairline,
                radius = baseRadius + 16.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            if (isRecording) {
                val r1 = baseRadius + (10f + animatedLevel * 22f).dp.toPx()
                val r2 = baseRadius + (20f + animatedLevel * 34f).dp.toPx()
                drawCircle(
                    color = ringColor.copy(alpha = 0.24f),
                    radius = r1,
                    center = center,
                )
                drawCircle(
                    color = ringColor.copy(alpha = 0.14f),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = orbColor,
            shadowElevation = if (isRecording) 8.dp else 4.dp,
            modifier = Modifier
                .size(78.dp)
                .testTag("record_voice_button"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isRecording) "توقف ضبط صدا" else "شروع ضبط صدا",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Bespoke top-bar model LED badge.
 */
@Composable
fun ModelStatusOrb(
    label: String,
    isReady: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = when {
        isReady -> MaterialTheme.colorScheme.secondary
        isLoading -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val bgColor = MaterialTheme.colorScheme.surface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

data class DockItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

/**
 * Bespoke floating sculpted capsule navigation dock with spring-animated label expansion
 * and icon scale pop. Always respects system navigation bar insets via `.navigationBarsPadding()`.
 */
@Composable
fun FloatingStudioDock(
    items: List<DockItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(50),
                ambientColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.route == currentRoute
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "dock_bg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "dock_fg",
                    )
                    val horizontalPad by animateDpAsState(
                        targetValue = if (selected) 18.dp else 14.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "dock_pad",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 0.96f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "dock_icon_scale",
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable { onSelect(item.route) }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = horizontalPad, vertical = 10.dp)
                            .testTag(item.testTag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = fg,
                            modifier = Modifier
                                .size(20.dp)
                                .scale(iconScale),
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(180)) + expandHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                            ),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = fg,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tactile dual-card narrator switcher for the Studio screen allowing one-tap selection
 * between built-in Female (خواننده زن - سارا) and Male (خواننده مرد - آرش) Persian voices,
 * while clearly indicating when a custom cloned voice from the Voices tab is active.
 */
@Composable
fun StudioNarratorSelector(
    selectedGender: NarratorGender,
    isCustomVoiceActive: Boolean,
    customVoiceLabel: String?,
    onSelectGender: (NarratorGender) -> Unit,
    onOpenCustomVoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "انتخاب جنسیت",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (isCustomVoiceActive && !customVoiceLabel.isNullOrBlank()) {
                    Surface(
                        onClick = onOpenCustomVoices,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                            )
                            Text(
                                text = "سفارشی: $customVoiceLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (selectedGender == NarratorGender.FEMALE) "سارا · ۲۱۵Hz" else "آرش · ۱۱۵Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NarratorGender.entries.forEach { gender ->
                    val isFemale = gender == NarratorGender.FEMALE
                    val isSelected = !isCustomVoiceActive && selectedGender == gender
                    val accentColor = if (isFemale) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                    val onAccentColor = if (isFemale) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondary
                    }

                    val cardBg by animateColorAsState(
                        targetValue = if (isSelected) {
                            accentColor
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                        },
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        label = "narrator_card_bg",
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        label = "narrator_card_border",
                    )
                    val titleColor by animateColorAsState(
                        targetValue = if (isSelected) onAccentColor else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "narrator_title_color",
                    )
                    val subtitleColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            onAccentColor.copy(alpha = 0.86f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "narrator_subtitle_color",
                    )
                    val avatarBg by animateColorAsState(
                        targetValue = if (isSelected) {
                            onAccentColor.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "narrator_avatar_bg",
                    )
                    val avatarTint by animateColorAsState(
                        targetValue = if (isSelected) onAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "narrator_avatar_tint",
                    )
                    val cardScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.02f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "narrator_card_scale",
                    )

                    Surface(
                        onClick = { onSelectGender(gender) },
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, borderColor),
                        shadowElevation = if (isSelected) 4.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .scale(cardScale)
                            .minimumInteractiveComponentSize()
                            .testTag(if (isFemale) "select_female_narrator" else "select_male_narrator"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(avatarBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isFemale) Icons.Rounded.Face3 else Icons.Rounded.Face6,
                                    contentDescription = gender.roleLabel,
                                    tint = avatarTint,
                                    modifier = Modifier.size(21.dp),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = gender.roleLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = titleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(onAccentColor),
                                        )
                                    }
                                }
                                Text(
                                    text = gender.timbreNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subtitleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
