package studio.buttonforge.geometry.bezier

import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.math.lerp

/** Cubic Bézier in absolute coordinates. A straight line has p1 and p2 on the chord. */
data class CubicBezier(val p0: Vec2, val p1: Vec2, val p2: Vec2, val p3: Vec2) {

    fun at(t: Double): Vec2 {
        val u = 1.0 - t
        return p0 * (u * u * u) + p1 * (3.0 * u * u * t) + p2 * (3.0 * u * t * t) + p3 * (t * t * t)
    }

    fun derivative(t: Double): Vec2 {
        val u = 1.0 - t
        return (p1 - p0) * (3.0 * u * u) + (p2 - p1) * (6.0 * u * t) + (p3 - p2) * (3.0 * t * t)
    }

    /**
     * Operation 1 (exact subdivision): De Casteljau split at [t].
     * left.at(u) == at(u * t) and right.at(u) == at(t + u * (1 - t)) up to floating-point error.
     */
    fun split(t: Double): Pair<CubicBezier, CubicBezier> {
        val p01 = lerp(p0, p1, t)
        val p12 = lerp(p1, p2, t)
        val p23 = lerp(p2, p3, t)
        val p012 = lerp(p01, p12, t)
        val p123 = lerp(p12, p23, t)
        val mid = lerp(p012, p123, t)
        return CubicBezier(p0, p01, p012, mid) to CubicBezier(mid, p123, p23, p3)
    }
}
