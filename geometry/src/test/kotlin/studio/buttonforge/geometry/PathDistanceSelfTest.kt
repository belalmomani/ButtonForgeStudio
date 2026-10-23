package studio.buttonforge.geometry

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import kotlin.test.Test
import kotlin.test.assertEquals

class PathDistanceSelfTest {
    private fun line(y: Double) = Contour(
        listOf(Anchor(AnchorId(1), Vec2(0.0, y)), Anchor(AnchorId(2), Vec2(100.0, y))),
        closed = false,
    )

    @Test
    fun identicalPathsHaveZeroDistance() {
        val c = TestShapes.random(7)
        assertEquals(0.0, PathDistance.hausdorff(c, c), 1e-12)
    }

    @Test
    fun parallelLinesHaveKnownDistance() {
        assertEquals(5.0, PathDistance.hausdorff(line(0.0), line(5.0)), 1e-9)
    }
}
