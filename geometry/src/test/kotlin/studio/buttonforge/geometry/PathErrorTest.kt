package studio.buttonforge.geometry

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.PathError
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.Contour
import kotlin.test.Test
import kotlin.test.assertEquals

class PathErrorTest {
    @Test
    fun identicalContoursHaveNoError() {
        val c = TestShapes.circle(100.0)
        assertEquals(0.0, PathError.between(c, c), 1e-9)
    }

    @Test
    fun aShiftedSquareIsExactlyTheShiftAway() {
        val a = TestShapes.square(100.0)
        val b = Contour(a.anchors.map { Anchor(it.id, Vec2(it.position.x + 5.0, it.position.y)) }, closed = true)
        assertEquals(5.0, PathError.between(a, b), 1e-6)
    }

    @Test
    fun pointsOnTheCurveHaveZeroDistance() {
        val c = TestShapes.circle(100.0)
        val pts = listOf(Vec2(100.0, 0.0), Vec2(0.0, 100.0), Vec2(-100.0, 0.0))
        assertEquals(0.0, PathError.maxDistance(pts, c), 1e-9)
        assertEquals(0.0, PathError.maxDistance(emptyList(), c))
    }
}
