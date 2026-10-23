package studio.buttonforge.geometry

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Contour
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Test-only metric: symmetric Hausdorff distance between two contours.
 * Each point of one contour is a point on its exact curve; the distance to the other contour is found by a
 * nearest-sample search followed by local golden-section refinement on the exact curve, so the polyline
 * sampling error does not enter the result. See PathDistanceSelfTest.
 */
object PathDistance {
    private const val SAMPLE_BUDGET = 20_000

    private fun samplesPerSegment(segmentCount: Int): Int = (SAMPLE_BUDGET / maxOf(1, segmentCount)).coerceIn(8, 2048)

    private class Sampled(contour: Contour) {
        val curves: List<CubicBezier> = contour.segments()
        val n: Int = samplesPerSegment(curves.size)
        val pts = ArrayList<Vec2>()
        val segOf = ArrayList<Int>()
        val tOf = ArrayList<Double>()

        init {
            for ((si, c) in curves.withIndex()) {
                for (i in 0..n) {
                    val t = i.toDouble() / n
                    pts += c.at(t); segOf += si; tOf += t
                }
            }
        }
    }

    fun polyline(contour: Contour): List<Vec2> = Sampled(contour).pts

    fun hausdorff(a: Contour, b: Contour): Double {
        val sa = Sampled(a)
        val sb = Sampled(b)
        return max(oneWay(sa, sb), oneWay(sb, sa))
    }

    private fun oneWay(from: Sampled, to: Sampled): Double {
        var worst = 0.0
        for (p in from.pts) worst = max(worst, distanceToPath(p, to))
        return worst
    }

    private fun distanceToPath(p: Vec2, s: Sampled): Double {
        var bestJ = 0
        var bestD2 = Double.MAX_VALUE
        for (j in s.pts.indices) {
            val dx = s.pts[j].x - p.x
            val dy = s.pts[j].y - p.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; bestJ = j }
        }
        var best = sqrt(bestD2)
        val dt = 1.0 / s.n
        for (j in (bestJ - 1)..(bestJ + 1)) {
            if (j !in s.pts.indices) continue
            val curve = s.curves[s.segOf[j]]
            best = min(best, refine(p, curve, max(0.0, s.tOf[j] - dt), min(1.0, s.tOf[j] + dt)))
        }
        return best
    }

    private fun refine(p: Vec2, c: CubicBezier, lo0: Double, hi0: Double): Double {
        var lo = lo0
        var hi = hi0
        val g = 0.6180339887498949
        repeat(60) {
            val m1 = hi - g * (hi - lo)
            val m2 = lo + g * (hi - lo)
            if (c.at(m1).distanceTo(p) < c.at(m2).distanceTo(p)) hi = m2 else lo = m1
        }
        return c.at((lo + hi) / 2.0).distanceTo(p)
    }

    fun diagonal(c: Contour): Double {
        val pts = polyline(c)
        val w = pts.maxOf { it.x } - pts.minOf { it.x }
        val h = pts.maxOf { it.y } - pts.minOf { it.y }
        return sqrt(w * w + h * h)
    }
}
