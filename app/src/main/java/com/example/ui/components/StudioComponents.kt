package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun InteractiveWaveformView(
    peaks: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.secondary
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val bars = if (peaks.isEmpty()) List(48) { 0.16f } else peaks

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val count = bars.size
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.58f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val normPos = (i + 0.5f) / count.toFloat()
            val amp = bars[i].coerceIn(0.08f, 1f)
            val barH = size.height * 0.86f * amp
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val y = centerY - barH / 2f

            val barColor = when {
                isPlaying && normPos <= progress -> activeColor
                isPlaying && normPos <= progress + 0.04f -> secondaryGlow
                else -> inactiveColor
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    dotColor: Color,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
fun MicLevelMeter(
    level: Float,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(targetValue = level.coerceIn(0f, 1f), label = "mic_lvl")
    val barColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val bars = 24
        val slot = size.width / bars
        val w = (slot * 0.55f).coerceAtLeast(3f)
        val centerY = size.height / 2f
        for (i in 0 until bars) {
            val distFromCenter = kotlin.math.abs(i - bars / 2f) / (bars / 2f)
            val shaped = (animated * (1.15f - distFromCenter * 0.65f)).coerceIn(0.1f, 1f)
            val h = size.height * shaped * 0.85f
            val x = i * slot + (slot - w) / 2f
            drawRoundRect(
                color = if (animated > 0.08f) barColor else trackColor,
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}
