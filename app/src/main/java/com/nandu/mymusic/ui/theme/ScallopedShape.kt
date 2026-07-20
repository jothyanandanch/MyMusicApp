package com.nandu.mymusic.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A soft, "cookie"/flower shaped [Shape] used for the Material-You style transport
 * controls (play/pause, previous, next) in the redesigned Now Playing screen.
 *
 * The outline radius oscillates smoothly around the button's center — [petals]
 * controls how many gentle bumps appear around the perimeter, and [amplitude]
 * controls how pronounced each bump is (as a fraction of the base radius).
 * Enough sample points are used that the curve reads as smooth/rounded rather
 * than as a jagged star, even at typical button sizes.
 */
class ScallopedShape(
    private val petals: Int = 10,
    private val amplitude: Float = 0.09f,
    private val rotationDegrees: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = min(size.width, size.height) / 2f
        val rotationRad = Math.toRadians(rotationDegrees.toDouble())
        val steps = petals * 16 // dense sampling keeps the curve looking smooth, not spiky

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val theta = t * 2.0 * Math.PI
            val r = baseRadius * (1f + amplitude * cos(petals * theta)).toFloat()
            val x = cx + r * cos(theta + rotationRad).toFloat()
            val y = cy + r * sin(theta + rotationRad).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
