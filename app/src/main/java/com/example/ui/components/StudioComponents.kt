package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.NarratorGender
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaSpacing
import com.example.ui.theme.CillaStroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Subtle hairline divider aligned to Avina's minimal visual grammar.
 */
@Composable
fun CillaHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = CillaStroke.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Clean, minimal acoustic waveform with smooth playhead indicator.
 */
@Composable
fun InteractiveWaveformView(
    peaks: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.50f)
    val bars = if (peaks.isEmpty()) List(40) { 0.22f } else peaks

    val smoothProgress by animateFloatAsState(
        targetValue = if (isPlaying) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "waveform_progress",
    )

    val liveEnergy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = CillaMotion.restrainedSpring(),
        label = "waveform_energy",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val eqPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "eq_phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        val count = bars.size
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.42f).coerceIn(2f, 3f)
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val normPos = (i + 0.5f) / count.toFloat()
            val baseAmp = bars[i].coerceIn(0.14f, 1f)

            val distFromHead = abs(normPos - smoothProgress)
            val headProximity = (1f - (distFromHead / 0.14f)).coerceIn(0f, 1f)
            val rippleBoost = if (liveEnergy > 0.01f) {
                val osc = 0.5f + 0.5f * sin(eqPhase * 1.8f + i * 0.55f).toFloat()
                liveEnergy * (0.04f * osc + 0.20f * headProximity * (0.5f + 0.5f * osc))
            } else {
                0f
            }

            val finalAmp = (baseAmp + rippleBoost).coerceIn(0.14f, 1f)
            val barH = size.height * 0.82f * finalAmp
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val y = centerY - barH / 2f

            val barColor = when {
                liveEnergy > 0.05f && normPos <= smoothProgress -> activeColor
                liveEnergy > 0.05f && headProximity > 0.4f -> activeColor.copy(alpha = 0.42f * liveEnergy)
                else -> inactiveColor
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }

        if (liveEnergy > 0.05f) {
            val headX = smoothProgress * size.width
            drawLine(
                color = activeColor.copy(alpha = liveEnergy),
                start = Offset(headX, 2.dp.toPx()),
                end = Offset(headX, size.height - 2.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Smooth geometric morphing Play (▶) ↔ Pause (⏸) icon using a damped spring Canvas path.
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
        animationSpec = CillaMotion.restrainedSpring(),
        label = "play_pause_morph",
    )

    val scalePulse = 1f - 0.06f * sin(morph.coerceIn(0f, 1f) * PI).toFloat()

    Canvas(
        modifier = modifier
            .size(20.dp)
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
            width = (w * 0.08f).coerceAtLeast(1.4f),
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
 * Understated circular playback trigger combining a restrained breathing ring while playing
 * and the smooth morphing Play/Pause icon.
 */
@Composable
fun AnimatedPlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 18.dp,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        animationSpec = CillaMotion.standardTween(),
        label = "playback_btn_bg",
    )

    val pulseTransition = rememberInfiniteTransition(label = "playback_halo")
    val pulseFraction by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1650, easing = CillaMotion.EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo_fraction",
    )

    val haloColor = MaterialTheme.colorScheme.primary
    val outerSize = buttonSize + 8.dp

    Box(
        modifier = Modifier
            .size(outerSize)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val minR = buttonSize.toPx() / 2f
                val maxR = size.minDimension / 2f
                val r = minR + (maxR - minR) * pulseFraction
                val alpha = (1f - pulseFraction) * 0.28f
                drawCircle(
                    color = haloColor.copy(alpha = alpha),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.25.dp.toPx()),
                )
            }
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = bgColor,
            modifier = modifier.size(buttonSize),
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
 * Compact studio microphone button with subtle level rings while recording.
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
        animationSpec = CillaMotion.restrainedSpring(),
        label = "orb_level",
    )
    val orbColor by animateColorAsState(
        targetValue = if (isRecording) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        animationSpec = CillaMotion.standardTween(),
        label = "orb_color",
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val hairline = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 32.dp.toPx()

            drawCircle(
                color = hairline,
                radius = baseRadius + 10.dp.toPx(),
                center = center,
                style = Stroke(width = CillaStroke.hairline.toPx()),
            )

            if (isRecording) {
                val r1 = baseRadius + (6f + animatedLevel * 14f).dp.toPx()
                val r2 = baseRadius + (12f + animatedLevel * 22f).dp.toPx()
                drawCircle(
                    color = ringColor.copy(alpha = 0.14f),
                    radius = r1,
                    center = center,
                )
                drawCircle(
                    color = ringColor.copy(alpha = 0.34f),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = orbColor,
            modifier = Modifier
                .size(64.dp)
                .testTag("record_voice_button"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isRecording) "توقف ضبط" else "ضبط صدا",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Minimal model status indicator pill for the Avina header.
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

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CillaRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
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

/**
 * Compact, restrained two-option horizontal segmented gender selector (زن / مرد).
 * Uses terracotta accent for the selected state without large cards, avatars, or helper text.
 */
@Composable
fun CompactGenderSelector(
    selectedGender: NarratorGender,
    isCustomVoiceActive: Boolean,
    onSelectGender: (NarratorGender) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(CillaRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(48.dp)
            .testTag("gender_segmented_selector"),
    ) {
        Row(
            modifier = Modifier
                .padding(3.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            NarratorGender.entries.forEach { gender ->
                val isFemale = gender == NarratorGender.FEMALE
                val isSelected = !isCustomVoiceActive && selectedGender == gender

                val segBg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    animationSpec = CillaMotion.quickTween(),
                    label = "gender_seg_bg",
                )
                val segFg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = CillaMotion.quickTween(),
                    label = "gender_seg_fg",
                )

                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(62.dp)
                        .clip(RoundedCornerShape(CillaRadius.sm))
                        .background(segBg)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            role = Role.RadioButton,
                            onClick = { onSelectGender(gender) },
                        )
                        .semantics {
                            selected = isSelected
                            contentDescription = gender.roleLabel
                        }
                        .testTag(if (isFemale) "select_female_narrator" else "select_male_narrator"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = gender.roleLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = segFg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Compact voice selector displaying the active voice name with a subtle dropdown affordance.
 */
@Composable
fun CompactVoiceSelector(
    voiceName: String,
    isCustomVoiceActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CillaRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = CillaStroke.hairline,
            color = if (isCustomVoiceActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier
            .height(48.dp)
            .testTag("active_voice_selector"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = "انتخاب صدا",
                tint = if (isCustomVoiceActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = voiceName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
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
 * Minimal bottom navigation bar separated by a single hairline.
 * Respects system gesture insets via `.navigationBarsPadding()`.
 */
@Composable
fun CillaBottomBar(
    items: List<DockItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        CillaHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CillaSpacing.sm, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == currentRoute
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                    },
                    animationSpec = CillaMotion.quickTween(),
                    label = "nav_tint",
                )
                val dotAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = CillaMotion.quickTween(),
                    label = "nav_dot",
                )

                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(CillaRadius.sm))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = { onSelect(item.route) },
                        )
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 5.dp)
                        .testTag(item.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha)),
                    )
                }
            }
        }
    }
}
