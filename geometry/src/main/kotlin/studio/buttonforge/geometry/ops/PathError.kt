package studio.buttonforge.geometry.ops

import studio.buttonforge.geometry.bezier.CurveNearest
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Contour
import kotlin.math.max

/** Distances between points and curves, used to report how far an approximate operation moved the shape. */
object PathError {
    /** Largest distance from any of [points] to the nearest point on [contour]. 0 for an empty list. */
    fun maxDistance(points: List<Vec2>, contour: Contour): Double {
        val curves = contour.segments()
        if (curves.isEmpty()) return if (points.isEmpty()) 0.0 else Double.POSITIVE_INFINITY
        var worst = 0.0
        for (p in points) {
            var best = Double.MAX_VALUE
            for (c in curves) best = minOf(best, CurveNearest.nearest(c, p).second)
            worst = max(worst, best)
        }
        return worst
    }

    /**
     * Estimated symmetric distance between two contours (each one's sample points against the other).
     * The sampling is fine enough that the estimate is accurate to about 0.1% of the shape size.
     */
    fun between(a: Contour, b: Contour): Double {
        val pa = Resampling.flatten(a, toleranceFor(a))
        val pb = Resampling.flatten(b, toleranceFor(b))
        return max(maxDistance(pa, b), maxDistance(pb, a))
    }

    private fun toleranceFor(c: Contour): Double {
        val corners = c.anchors.map { it.position }
        if (corners.isEmpty()) return 1e-3
        val w = corners.maxOf { it.x } - corners.minOf { it.x }
        val h = corners.maxOf { it.y } - corners.minOf { it.y }
        return max(1e-6, 1e-3 * kotlin.math.hypot(w, h))
    }
}
