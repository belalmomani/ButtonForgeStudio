package studio.buttonforge.geometry

import studio.buttonforge.geometry.bezier.ArcLength
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArcLengthDistributionTest {
    private fun arcPosition(c: Contour, segIdx: Int, t: Double): Double {
        val segs = c.segments()
        var s = 0.0
        for (i in 0 until segIdx) s += ArcLength.length(segs[i])
        return s + ArcLength.length(segs[segIdx], 0.0, t)
    }

    @Test
    fun spacingIsEvenByArcLengthForRequestedCounts() {
        val c = TestShapes.circle(100.0)
        val total = c.segments().sumOf { ArcLength.length(it) }
        for (count in listOf(25, 50, 100, 250, 500, 1000)) {
            val params = ArcLengthDistribution.distribute(c, count)
            assertEquals(count, params.size)
            val positions = params.map { arcPosition(c, it.segmentIndex, it.t) }
            for (k in 0 until count) {
                assertEquals(k * total / count, positions[k], 1e-7 * total, "count=$count k=$k")
            }
        }
    }

    @Test
    fun circleLengthMatchesAnalyticWithinApproximationError() {
        val total = TestShapes.circle(100.0).segments().sumOf { ArcLength.length(it) }
        assertEquals(2 * PI * 100.0, total, 2 * PI * 100.0 * 5e-4)
    }

    @Test
    fun distributionDoesNotModifyTheContour() {
        val c = TestShapes.random(3)
        val before = c.copy()
        ArcLengthDistribution.distribute(c, 100)
        assertEquals(before, c)
    }

    @Test
    fun zeroLengthContourReturnsEmptyWithoutNaN() {
        val p = Vec2(5.0, 5.0)
        val c = Contour(
            listOf(Anchor(AnchorId(1), p), Anchor(AnchorId(2), p), Anchor(AnchorId(3), p)),
            closed = true,
        )
        assertTrue(ArcLengthDistribution.distribute(c, 25).isEmpty())
    }

    @Test
    fun openContourIncludesBothEnds() {
        val c = Contour(
            listOf(Anchor(AnchorId(1), Vec2(0.0, 0.0)), Anchor(AnchorId(2), Vec2(100.0, 0.0))),
            closed = false,
        )
        val params = ArcLengthDistribution.distribute(c, 5)
        assertEquals(5, params.size)
        assertEquals(0.0, params.first().t, 1e-12)
        assertEquals(1.0, params.last().t, 1e-12)
    }

    @Test
    fun invalidArgumentsAreRejected() {
        val c = TestShapes.square()
        assertFailsWith<IllegalArgumentException> { ArcLengthDistribution.distribute(c, 0) }
        assertFailsWith<IllegalArgumentException> {
            Anchor(AnchorId(1), Vec2(Double.NaN, 0.0))
        }
    }
}
