package studio.buttonforge.geometry

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Contour
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Test-only metric: symmetric Hausdorff distance between two contours, via dense polylines.
 * Accuracy is bounded by the sampling (see PathDistanceSelfTest); not a certified bound.
 */
object PathDistance {
    private const val SAMPLE_BUDGET = 20_000

    /** Fewer samples per segment when a contour has many segments, so the O(n*m) distance stays fast. */
    private fun samplesPerSegment(segmentCount: Int): Int = (SAMPLE_BUDGET / maxOf(1, segmentCount)).coerceIn(8, 2048)

    fun polyline(contour: Contour): List<Vec2> {
        val pts = ArrayList<Vec2>()
        val n = samplesPerSegment(contour.segmentCount)
        for (seg: CubicBezier in contour.segments()) {
            for (i in 0..n) pts += seg.at(i.toDouble() / n)
        }
        return pts
    }

    fun hausdorff(a: Contour, b: Contour): Double {
        val pa = polyline(a)
        val pb = polyline(b)
        return max(oneWay(pa, pb), oneWay(pb, pa))
    }

    private fun oneWay(from: List<Vec2>, to: List<Vec2>): Double {
        var worst = 0.0
        for (p in from) {
            var best = Double.MAX_VALUE
            for (i in 0 until to.size - 1) best = min(best, distToSegment(p, to[i], to[i + 1]))
            worst = max(worst, best)
        }
        return worst
    }

    private fun distToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 == 0.0) return p.distanceTo(a)
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0.0, 1.0)
        val proj = Vec2(a.x + ab.x * t, a.y + ab.y * t)
        return sqrt((p.x - proj.x) * (p.x - proj.x) + (p.y - proj.y) * (p.y - proj.y))
    }

    fun diagonal(c: Contour): Double {
        val pts = polyline(c)
        val w = pts.maxOf { it.x } - pts.minOf { it.x }
        val h = pts.maxOf { it.y } - pts.minOf { it.y }
        return sqrt(w * w + h * h)
    }
}
