package app.orchard.tv.ui.home

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A rigid camera transform, not a warp. Overscan always covers the small translation on both axes. */
internal object AluCamera {
    const val CYCLE_MS = 100_000
    data class Pose(val scale: Float, val x: Float, val y: Float)

    fun pose(phase: Float): Pose {
        val angle = phase * 2.0 * PI
        return Pose(
            scale = 1.035f + 0.015f * cos(angle).toFloat(),
            x = 0.006f * sin(angle).toFloat(),
            y = 0.004f * cos(angle).toFloat(),
        )
    }
}
