package app.orchard.tv.ui.home

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class AluCameraTest {
    @Test fun everyCameraPoseCoversTheViewport() {
        for (step in 0..2000) {
            val pose = AluCamera.pose(step / 2000f)
            val margin = (pose.scale - 1f) / 2f
            assertTrue("Horizontal edge at $step", abs(pose.x) < margin)
            assertTrue("Vertical edge at $step", abs(pose.y) < margin)
        }
    }

    @Test fun cycleHasNoPositionJump() {
        val start = AluCamera.pose(0f)
        val end = AluCamera.pose(1f)
        assertEquals(start.scale, end.scale, 0.000001f)
        assertEquals(start.x, end.x, 0.000001f)
        assertEquals(start.y, end.y, 0.000001f)
    }

    @Test fun cycleHasNoVelocityReversalAtTheSeam() {
        val before = AluCamera.pose(0.999f)
        val seam = AluCamera.pose(0f)
        val after = AluCamera.pose(0.001f)
        assertEquals(seam.x - before.x, after.x - seam.x, 0.000001f)
        assertEquals(seam.y - before.y, after.y - seam.y, 0.000001f)
        assertEquals(seam.scale - before.scale, after.scale - seam.scale, 0.000001f)
    }
}
