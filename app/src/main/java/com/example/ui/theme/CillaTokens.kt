package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens for the Cilla Atelier design system.
 * Rooted in an 8dp spatial grid, crisp architectural hairlines,
 * and restrained, purposeful motion curves.
 */
object CillaSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val screenHorizontal = 22.dp
    val maxContentWidth = 600.dp
}

object CillaRadius {
    val sm = 6.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 14.dp
}

object CillaStroke {
    val hairline = 0.75.dp
    val emphasis = 1.25.dp
}

object CillaMotion {
    const val QuickMs = 170
    const val StandardMs = 240
    const val PageMs = 280

    val EaseOut: Easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    fun <T> standardTween() = tween<T>(durationMillis = StandardMs, easing = EaseOut)
    fun <T> quickTween() = tween<T>(durationMillis = QuickMs, easing = EaseOut)
    fun <T> restrainedSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

val CillaShapes = Shapes(
    extraSmall = RoundedCornerShape(CillaRadius.sm),
    small = RoundedCornerShape(CillaRadius.md),
    medium = RoundedCornerShape(CillaRadius.lg),
    large = RoundedCornerShape(CillaRadius.xl),
    extraLarge = RoundedCornerShape(28.dp),
)
