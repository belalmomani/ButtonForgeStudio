package studio.buttonforge.geometry.bezier

import studio.buttonforge.geometry.math.Vec2
import kotlin.math.max
import kotlin.math.min

/** Nearest point on one cubic: coarse scan, then golden-section refinement around the best sample. */
internal object CurveNearest {
    private const val COARSE = 24

    /** Returns (t, distance). */
    fun nearest(c: CubicBezier, p: Vec2): Pair<Double, Double> {
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        for (i in 0..COARSE) {
            val t = i.toDouble() / COARSE
            val d = c.at(t).distanceTo(p)
            if (d < bestD) {
                bestD = d
                bestT = t
            }
        }
        var lo = max(0.0, bestT - 1.0 / COARSE)
        var hi = min(1.0, bestT + 1.0 / COARSE)
        val g = 0.6180339887498949
        repeat(50) {
            val m1 = hi - g * (hi - lo)
            val m2 = lo + g * (hi - lo)
            if (c.at(m1).distanceTo(p) < c.at(m2).distanceTo(p)) hi = m2 else lo = m1
        }
        val t = (lo + hi) / 2.0
        return t to c.at(t).distanceTo(p)
    }
}
