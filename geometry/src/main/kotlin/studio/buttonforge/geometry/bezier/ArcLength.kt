package studio.buttonforge.geometry.bezier

import kotlin.math.abs

/** Arc length of cubic Béziers: composite 5-point Gauss–Legendre. Used only for measuring, never edits paths. */
object ArcLength {
    private val NODES = doubleArrayOf(
        0.0, -0.5384693101056831, 0.5384693101056831, -0.9061798459386640, 0.9061798459386640,
    )
    private val WEIGHTS = doubleArrayOf(
        0.5688888888888889, 0.4786286704993665, 0.4786286704993665, 0.2369268850561891, 0.2369268850561891,
    )
    private const val SUBINTERVALS = 8

    fun length(curve: CubicBezier, t0: Double = 0.0, t1: Double = 1.0): Double {
        if (t1 <= t0) return 0.0
        val h = (t1 - t0) / SUBINTERVALS
        var total = 0.0
        for (i in 0 until SUBINTERVALS) {
            val a = t0 + i * h
            val half = h / 2.0
            val mid = a + half
            var sum = 0.0
            for (k in NODES.indices) {
                sum += WEIGHTS[k] * curve.derivative(mid + half * NODES[k]).length()
            }
            total += sum * half
        }
        return total
    }

    /** Parameter t in [0,1] whose arc length from t=0 equals [s] (clamped). Bisection. */
    fun parameterAtLength(curve: CubicBezier, s: Double, total: Double = length(curve)): Double {
        if (s <= 0.0) return 0.0
        if (s >= total) return 1.0
        var lo = 0.0
        var hi = 1.0
        repeat(60) {
            val mid = (lo + hi) / 2.0
            if (length(curve, 0.0, mid) < s) lo = mid else hi = mid
            if (abs(hi - lo) < 1e-15) return (lo + hi) / 2.0
        }
        return (lo + hi) / 2.0
    }
}
