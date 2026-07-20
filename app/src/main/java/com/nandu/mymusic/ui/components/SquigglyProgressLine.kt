package com.nandu.mymusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws a Material-You style "squiggly" waveform seek-bar track: a continuously
 * animating sine wave for the played portion and a calmer, dimmer wave for the
 * remaining portion — similar to Android's expressive media progress indicator.
 *
 * This composable only draws the track. Drag/tap handling stays with the caller
 * (see NowPlayingScreen's BoxWithConstraints) so existing gesture logic doesn't change.
 */
@Composable
fun SquigglyProgressLine(
    progressFraction: Float,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    isAnimating: Boolean = true,
    amplitudeDp: Dp = 3.dp,
    wavelengthDp: Dp = 18.dp,
    strokeWidthDp: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squigglyPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "squigglyPhaseAnim"
    )

    Canvas(modifier = modifier) {
        val amplitude = amplitudeDp.toPx()
        val wavelength = wavelengthDp.toPx()
        val strokeWidth = strokeWidthDp.toPx()
        val centerY = size.height / 2f
        val width = size.width
        val activeX = (width * progressFraction.coerceIn(0f, 1f))
        val effectivePhase = if (isAnimating) phase else 0f

        fun wavePath(fromX: Float, toX: Float): Path {
            val path = Path()
            var x = fromX
            var first = true
            val step = 3f
            while (x < toX) {
                val y = centerY + amplitude * sin((2f * PI.toFloat() * x / wavelength) + effectivePhase)
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += step
            }
            val yEnd = centerY + amplitude * sin((2f * PI.toFloat() * toX / wavelength) + effectivePhase)
            if (first) path.moveTo(toX, yEnd) else path.lineTo(toX, yEnd)
            return path
        }

        // Remaining (inactive) portion — dimmer
        if (activeX < width) {
            drawPath(
                path = wavePath(activeX, width),
                color = inactiveColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Played (active) portion
        if (activeX > 0f) {
            drawPath(
                path = wavePath(0f, activeX),
                color = activeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
