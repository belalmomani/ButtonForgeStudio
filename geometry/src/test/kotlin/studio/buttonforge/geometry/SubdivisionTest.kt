package studio.buttonforge.geometry

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.path.withInsertedAnchors
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubdivisionTest {
    @Test
    fun splitIsParametricallyExact() {
        val rnd = Random(42)
        repeat(200) {
            fun p() = Vec2(rnd.nextDouble(-100.0, 100.0), rnd.nextDouble(-100.0, 100.0))
            val c = CubicBezier(p(), p(), p(), p())
            val t = rnd.nextDouble(0.01, 0.99)
            val (l, r) = c.split(t)
            val scale = 400.0
            for (i in 0..20) {
                val u = i / 20.0
                assertTrue(l.at(u).distanceTo(c.at(u * t)) <= 1e-12 * scale)
                assertTrue(r.at(u).distanceTo(c.at(t + u * (1 - t))) <= 1e-12 * scale)
            }
        }
    }

    @Test
    fun insertingDistributedAnchorsPreservesShapeForAllCounts() {
        for (count in listOf(25, 50, 100, 250, 500)) {
            val original = TestShapes.random(seed = count.toLong())
            val ids = IdSource()
            val params = ArcLengthDistribution.distribute(original, count)
                .filter { it.t > 1e-9 && it.t < 1 - 1e-9 } // parameters at segment ends are existing anchors
            val result = original.withInsertedAnchors(params, ids::nextId)
            assertEquals(original.anchors.size + params.size, result.anchors.size)
            assertEquals(result.anchors.size, result.anchors.map { it.id }.toSet().size)
            val d = PathDistance.hausdorff(original, result)
            assertTrue(d <= 1e-6 * PathDistance.diagonal(original), "count=$count distance=$d")
        }
    }

    @Test
    fun originalAnchorIdsAreKept() {
        val original = TestShapes.square()
        val result = original.withInsertedAnchors(
            ArcLengthDistribution.distribute(original, 12).filter { it.t > 1e-9 },
            IdSource()::nextId,
        )
        assertTrue(result.anchors.map { it.id }.containsAll(original.anchors.map { it.id }))
    }
}
