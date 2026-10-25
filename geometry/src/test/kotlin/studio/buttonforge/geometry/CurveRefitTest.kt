package studio.buttonforge.geometry

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.CurveRefit
import studio.buttonforge.geometry.ops.PathError
import studio.buttonforge.geometry.ops.Resampling
import studio.buttonforge.geometry.path.CornerType
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CurveRefitTest {
    private fun circlePoints(n: Int, r: Double) =
        List(n) { i -> Vec2(r * cos(2 * Math.PI * i / n), r * sin(2 * Math.PI * i / n)) }

    private fun squarePoints(perSide: Int, side: Double): List<Vec2> {
        val corners = listOf(Vec2(0.0, 0.0), Vec2(side, 0.0), Vec2(side, side), Vec2(0.0, side))
        return corners.indices.flatMap { k ->
            val a = corners[k]
            val b = corners[(k + 1) % 4]
            List(perSide) { i -> Vec2(a.x + (b.x - a.x) * i / perSide, a.y + (b.y - a.y) * i / perSide) }
        }
    }

    @Test
    fun aSmoothClosedCurveFitsWithFewSmoothAnchorsAndReportsItsRealError() {
        val points = circlePoints(200, 100.0)
        val result = CurveRefit.fit(points, closed = true, tolerance = 0.5, nextId = IdSource()::nextId)
        assertTrue(result.withinTolerance)
        assertTrue(result.contour.closed)
        assertTrue(result.contour.anchors.size <= 12, "anchors=${result.contour.anchors.size}")
        assertTrue(result.contour.anchors.all { it.corner == CornerType.Smooth })
        val independent = PathError.maxDistance(points, result.contour)
        assertTrue(independent <= 0.5 + 1e-9, "error=$independent")
        assertEquals(independent, result.maxError, 1e-6)
    }

    @Test
    fun cornersAreKeptSharpAndSidesStayStraight() {
        val result = CurveRefit.fit(squarePoints(100, 100.0), closed = true, tolerance = 0.01, nextId = IdSource()::nextId)
        val anchors = result.contour.anchors
        assertEquals(4, anchors.size)
        assertTrue(anchors.all { it.corner == CornerType.Sharp })
        val expected = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0))
        for ((a, e) in anchors.zip(expected)) assertTrue(a.position.distanceTo(e) < 1e-9, "${a.position} vs $e")
        assertTrue(result.withinTolerance)
    }

    @Test
    fun aStarSurvivesFlattenAndRefitUnchanged() {
        val ids = IdSource()
        val star = Primitives.star(Vec2.ZERO, 100.0, 40.0, 5, ids::nextId).contours.single()
        val points = Resampling.flatten(star, 0.01)
        val result = CurveRefit.fit(points, closed = true, tolerance = 0.01, nextId = ids::nextId)
        assertEquals(10, result.contour.anchors.size)
        assertTrue(result.contour.anchors.all { it.corner == CornerType.Sharp })
        for ((a, b) in star.anchors.zip(result.contour.anchors)) assertTrue(a.position.distanceTo(b.position) < 1e-9)
        assertTrue(PathError.between(star, result.contour) < 1e-6)
    }

    @Test
    fun anOpenSCurveFitsWithinTolerance() {
        val s = CubicBezier(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 100.0), Vec2(100.0, 100.0))
        val points = List(100) { s.at(it / 99.0) }
        val result = CurveRefit.fit(points, closed = false, tolerance = 0.2, nextId = IdSource()::nextId)
        assertTrue(result.withinTolerance)
        assertTrue(!result.contour.closed)
        assertTrue(result.contour.anchors.size in 2..6, "anchors=${result.contour.anchors.size}")
        assertTrue(PathError.maxDistance(points, result.contour) <= 0.2 + 1e-9)
    }

    @Test
    fun repeatedPointsAreIgnored() {
        val base = squarePoints(20, 50.0)
        val noisy = base.flatMap { listOf(it, it) } + base.first()
        val result = CurveRefit.fit(noisy, closed = true, tolerance = 0.05, nextId = IdSource()::nextId)
        assertEquals(4, result.contour.anchors.size)
    }

    @Test
    fun badInputIsRejected() {
        val ids = IdSource()
        assertFailsWith<IllegalArgumentException> { CurveRefit.fit(listOf(Vec2.ZERO), false, 1.0, nextId = ids::nextId) }
        assertFailsWith<IllegalArgumentException> {
            CurveRefit.fit(listOf(Vec2.ZERO, Vec2(1.0, 0.0)), true, 1.0, nextId = ids::nextId)
        }
        assertFailsWith<IllegalArgumentException> { CurveRefit.fit(circlePoints(10, 5.0), true, 0.0, nextId = ids::nextId) }
    }

    @Test
    fun interpolationPassesThroughEveryPointAndKeepsTheCount() {
        for (n in listOf(24, 100)) {
            val points = circlePoints(n, 100.0)
            val c = CurveRefit.interpolate(points, closed = true, nextId = IdSource()::nextId)
            assertEquals(n, c.anchors.size)
            for ((a, p) in c.anchors.zip(points)) assertEquals(p, a.position)
            val reference = circlePoints(400, 100.0)
            val limit = if (n == 24) 0.5 else 0.05
            assertTrue(PathError.maxDistance(reference, c) <= limit, "n=$n")
        }
    }

    @Test
    fun interpolatingAnOpenLineKeepsItStraight() {
        val points = List(6) { Vec2(it * 20.0, 0.0) }
        val c = CurveRefit.interpolate(points, closed = false, nextId = IdSource()::nextId)
        assertEquals(6, c.anchors.size)
        assertTrue(c.segments().all { s -> listOf(s.p1, s.p2).all { abs(it.y) < 1e-12 } })
    }

    @Test
    fun interpolationRejectsTooFewPoints() {
        val ids = IdSource()
        assertFailsWith<IllegalArgumentException> { CurveRefit.interpolate(listOf(Vec2.ZERO), false, ids::nextId) }
        assertFailsWith<IllegalArgumentException> {
            CurveRefit.interpolate(listOf(Vec2.ZERO, Vec2(1.0, 0.0)), true, ids::nextId)
        }
    }
}
