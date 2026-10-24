package studio.buttonforge.geometry

import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.path.withInsertedAnchors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FillToCountTest {
    @Test
    fun reachesExactlyTheRequestedCountAndKeepsTheShape() {
        for (target in listOf(25, 50, 100, 250, 500, 1000)) {
            val original = TestShapes.random(seed = target.toLong())
            val params = ArcLengthDistribution.fillTo(original, target)
            val result = original.withInsertedAnchors(params, IdSource()::nextId)
            assertEquals(target, result.anchors.size, "target=$target")
            assertEquals(target, result.anchors.map { it.id }.toSet().size)
            val d = PathDistance.hausdorff(original, result)
            assertTrue(d <= 1e-6 * PathDistance.diagonal(original), "target=$target distance=$d")
        }
    }

    @Test
    fun alreadyAtTargetNeedsNothing() {
        val c = TestShapes.square()
        assertTrue(ArcLengthDistribution.fillTo(c, 4).isEmpty())
    }

    @Test
    fun spacingIsEvenOnASquare() {
        val c = TestShapes.square(100.0)
        val result = c.withInsertedAnchors(ArcLengthDistribution.fillTo(c, 8), IdSource()::nextId)
        assertEquals(8, result.anchors.size)
        // 4 extra points go one per side, at the middle
        assertEquals(50.0, result.anchors[1].position.x, 1e-9)
    }

    @Test
    fun targetBelowCurrentCountIsRejected() {
        assertFailsWith<IllegalArgumentException> { ArcLengthDistribution.fillTo(TestShapes.square(), 3) }
    }
}
