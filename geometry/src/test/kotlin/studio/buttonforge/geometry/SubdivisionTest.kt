package studio.buttonforge.geometry

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.path.SegmentParam
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
                .filter { it.t > 1e-9 && it.t < 1 - 1e-9 } // end parameters are existing anchors and add nothing
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
            ArcLengthDistribution.distribute(original, 12),
            IdSource()::nextId,
        )
        assertTrue(result.anchors.map { it.id }.containsAll(original.anchors.map { it.id }))
    }

    @Test
    fun parametersAtSegmentEndsInsertNothing() {
        val c = TestShapes.square()
        val same = c.withInsertedAnchors(listOf(SegmentParam(0, 0.0), SegmentParam(1, 1.0)), IdSource()::nextId)
        assertEquals(c, same)
    }

    @Test
    fun insertedSegmentsMatchOriginalParametrically() {
        for (count in listOf(25, 100, 500)) {
            val original = TestShapes.random(seed = count.toLong())
            val params = ArcLengthDistribution.distribute(original, count)
                .filter { it.t > 1e-9 && it.t < 1 - 1e-9 }
            val result = original.withInsertedAnchors(params, IdSource()::nextId)
            val resultSegs = result.segments()
            var next = 0
            for (si in 0 until original.segmentCount) {
                val ts = params.filter { it.segmentIndex == si }.map { it.t }.sorted()
                val bounds = listOf(0.0) + ts + listOf(1.0)
                for (k in 0 until bounds.size - 1) {
                    val sub = resultSegs[next++]
                    for (u in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                        val expected = original.segment(si).at(bounds[k] + u * (bounds[k + 1] - bounds[k]))
                        assertTrue(
                            sub.at(u).distanceTo(expected) <= 1e-9,
                            "count=$count segment=$si piece=$k u=$u error=${sub.at(u).distanceTo(expected)}",
                        )
                    }
                }
            }
            assertEquals(resultSegs.size, next)
        }
    }
}
