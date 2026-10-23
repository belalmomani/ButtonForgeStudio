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
        var maxStep = 0.0
        lateinit var d: DoubleArray

        init {
            for ((si, c) in curves.withIndex()) {
                for (i in 0..n) {
                    val t = i.toDouble() / n
                    pts += c.at(t); segOf += si; tOf += t
                    if (i > 0) maxStep = max(maxStep, pts[pts.size - 1].distanceTo(pts[pts.size - 2]))
                }
            }
            d = DoubleArray(pts.size)
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
        val d = s.d
        var bestD = Double.MAX_VALUE
        for (j in s.pts.indices) {
            d[j] = s.pts[j].distanceTo(p)
            if (d[j] < bestD) bestD = d[j]
        }
        // Refine around every local minimum that could hide the true nearest point (e.g. at self-crossings,
        // where another branch can be closer to p than the nearest sample of the right branch).
        val threshold = bestD + 2.0 * s.maxStep
        var best = bestD
        val dt = 1.0 / s.n
        for (j in s.pts.indices) {
            if (d[j] > threshold) continue
            if (j > 0 && d[j] > d[j - 1]) continue
            if (j < s.pts.lastIndex && d[j] > d[j + 1]) continue
            for (k in (j - 1)..(j + 1)) {
                if (k !in s.pts.indices) continue
                val curve = s.curves[s.segOf[k]]
                best = min(best, refine(p, curve, max(0.0, s.tOf[k] - dt), min(1.0, s.tOf[k] + dt)))
            }
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
