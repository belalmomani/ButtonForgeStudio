package studio.buttonforge.geometry

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.PathError
import studio.buttonforge.geometry.ops.Resampling
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResamplingTest {
    @Test
    fun sampledPointsLieOnTheCurveAndAreEvenlySpaced() {
        val circle = TestShapes.circle(100.0)
        for (count in listOf(25, 50, 100, 500)) {
            val pts = Resampling.sample(circle, count)
            assertEquals(count, pts.size)
            for (p in pts) assertTrue(abs(p.length() - 100.0) <= 0.05, "count=$count radius=${p.length()}")
            val gaps = pts.indices.map { pts[it].distanceTo(pts[(it + 1) % count]) }
            val mean = gaps.average()
            for (g in gaps) assertTrue(abs(g - mean) <= 0.01 * mean, "count=$count gap=$g mean=$mean")
        }
    }

    @Test
    fun openContoursIncludeBothEnds() {
        val line = Contour(
            listOf(Anchor(AnchorId(1), Vec2(0.0, 0.0)), Anchor(AnchorId(2), Vec2(100.0, 0.0))),
            closed = false,
        )
        val xs = Resampling.sample(line, 5).map { it.x }
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), xs.map { Math.round(it * 1e9) / 1e9 })
    }

    @Test
    fun aContourWithNoLengthGivesNoPoints() {
        val p = Vec2(3.0, 3.0)
        val c = Contour(listOf(Anchor(AnchorId(1), p), Anchor(AnchorId(2), p), Anchor(AnchorId(3), p)), closed = true)
        assertTrue(Resampling.sample(c, 10).isEmpty())
    }

    @Test
    fun flattenStaysWithinTheToleranceAndClosesTheLoop() {
        val circle = TestShapes.circle(100.0)
        for (tol in listOf(1.0, 0.1, 0.01)) {
            val poly = Resampling.flatten(circle, tol)
            assertEquals(poly.first(), poly.last())
            val mids = poly.zipWithNext { a, b -> Vec2((a.x + b.x) / 2, (a.y + b.y) / 2) }
            assertTrue(PathError.maxDistance(mids, circle) <= tol + 1e-9, "tol=$tol")
        }
        assertTrue(Resampling.flatten(circle, 0.01).size > Resampling.flatten(circle, 1.0).size)
    }

    @Test
    fun straightEdgesFlattenToTheirCorners() {
        assertEquals(5, Resampling.flatten(TestShapes.square(), 0.01).size) // 4 corners and the closing point
    }

    @Test
    fun badToleranceIsRejected() {
        assertFailsWith<IllegalArgumentException> { Resampling.flatten(TestShapes.square(), 0.0) }
    }

    @Test
    fun polylineResamplingKeepsTheEndsAndTheSpacing() {
        val line = listOf(Vec2(0.0, 0.0), Vec2(30.0, 0.0), Vec2(100.0, 0.0))
        val pts = Resampling.resamplePolyline(line, 25.0)
        assertEquals(Vec2(0.0, 0.0), pts.first())
        assertEquals(Vec2(100.0, 0.0), pts.last())
        assertTrue(pts.size in 5..6, "size=${pts.size}")
        for (g in pts.zipWithNext { a, b -> a.distanceTo(b) }) assertTrue(g <= 25.0 + 1e-9, "gap=$g")
        assertEquals(2, Resampling.resamplePolyline(listOf(Vec2.ZERO, Vec2(5.0, 0.0)), 25.0).size)
        assertEquals(1, Resampling.resamplePolyline(listOf(Vec2.ZERO), 25.0).size)
        assertFailsWith<IllegalArgumentException> { Resampling.resamplePolyline(line, 0.0) }
    }

    @Test
    fun smoothingCalmsJitterButKeepsTheEnds() {
        val zigzag = List(21) { Vec2(it * 5.0, if (it % 2 == 0) 1.0 else -1.0) }
        val smooth = Resampling.smoothPolyline(zigzag, 2)
        assertEquals(zigzag.first(), smooth.first())
        assertEquals(zigzag.last(), smooth.last())
        assertTrue(smooth.subList(3, 18).all { abs(it.y) < 0.5 })
        val straight = List(10) { Vec2(it * 3.0, 7.0) }
        assertEquals(straight, Resampling.smoothPolyline(straight, 3).map { Vec2(Math.round(it.x * 1e9) / 1e9, it.y) })
    }
}
