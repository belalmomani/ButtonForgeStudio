package studio.buttonforge.geometry

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrimitivesTest {
    @Test
    fun circleStaysWithinThePublishedApproximationError() {
        val center = Vec2(50.0, -20.0)
        val c = Primitives.circle(center, 100.0, IdSource()::nextId).contours.single()
        for (p in PathDistance.polyline(c)) {
            assertTrue(abs(p.distanceTo(center) - 100.0) <= 0.05, "radial error ${p.distanceTo(center) - 100.0}")
        }
        assertEquals(4, c.anchors.size)
        assertTrue(c.closed)
    }

    @Test
    fun rectangleHasTheRequestedBounds() {
        val c = Primitives.rectangle(Vec2(10.0, 20.0), 40.0, 10.0, IdSource()::nextId).contours.single()
        assertEquals(listOf(-10.0, 30.0, 30.0, -10.0), c.anchors.map { it.position.x })
        assertEquals(listOf(15.0, 15.0, 25.0, 25.0), c.anchors.map { it.position.y })
    }

    @Test
    fun starAlternatesOuterAndInnerRadiusStartingAtTheTop() {
        val center = Vec2(0.0, 0.0)
        val c = Primitives.star(center, 100.0, 40.0, 5, IdSource()::nextId).contours.single()
        assertEquals(10, c.anchors.size)
        for ((i, a) in c.anchors.withIndex()) {
            assertEquals(if (i % 2 == 0) 100.0 else 40.0, a.position.distanceTo(center), 1e-9)
        }
        assertEquals(0.0, c.anchors[0].position.x, 1e-9)
        assertEquals(-100.0, c.anchors[0].position.y, 1e-9)
    }

    @Test
    fun idsComeFromTheProviderAndAreUnique() {
        val ids = IdSource(10)
        val c = Primitives.star(Vec2.ZERO, 10.0, 4.0, 6, ids::nextId).contours.single()
        assertEquals((10L..21L).toList(), c.anchors.map { it.id.value })
    }

    @Test
    fun invalidSizesAreRejected() {
        val ids = IdSource()
        assertFailsWith<IllegalArgumentException> { Primitives.circle(Vec2.ZERO, 0.0, ids::nextId) }
        assertFailsWith<IllegalArgumentException> { Primitives.rectangle(Vec2.ZERO, -1.0, 5.0, ids::nextId) }
        assertFailsWith<IllegalArgumentException> { Primitives.star(Vec2.ZERO, 10.0, 10.0, 5, ids::nextId) }
        assertFailsWith<IllegalArgumentException> { Primitives.star(Vec2.ZERO, 10.0, 4.0, 2, ids::nextId) }
    }
}
