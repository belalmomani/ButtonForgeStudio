package studio.buttonforge.geometry.ops

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Contour

/**
 * Operation 3: turns a contour into raw points. Read-only; the points are exact points on the curves, so the
 * result is a sampling of the shape, not a new shape.
 */
object Resampling {
    /**
     * Points equally spaced by arc length. Closed: [count] points starting at the first anchor. Open: [count]
     * points including both ends. Empty when the contour has no length.
     */
    fun sample(contour: Contour, count: Int): List<Vec2> =
        ArcLengthDistribution.distribute(contour, count).map { contour.segment(it.segmentIndex).at(it.t) }

    /**
     * An adaptive polyline whose straight pieces stay within [tolerance] of the curves. Starts at the first
     * anchor; for a closed contour the last point equals the first.
     */
    fun flatten(contour: Contour, tolerance: Double): List<Vec2> {
        require(tolerance > 0.0 && tolerance.isFinite()) { "tolerance must be positive" }
        val out = ArrayList<Vec2>()
        for ((i, segment) in contour.segments().withIndex()) {
            if (i == 0) out += segment.p0
            flattenSegment(segment, tolerance, out, 0)
        }
        return out
    }

    private fun flattenSegment(c: CubicBezier, tolerance: Double, out: MutableList<Vec2>, depth: Int) {
        if (depth >= MAX_DEPTH || isFlat(c, tolerance)) {
            out += c.p3
            return
        }
        val (left, right) = c.split(0.5)
        flattenSegment(left, tolerance, out, depth + 1)
        flattenSegment(right, tolerance, out, depth + 1)
    }

    private fun isFlat(c: CubicBezier, tolerance: Double) =
        distanceToSegment(c.p1, c.p0, c.p3) <= tolerance && distanceToSegment(c.p2, c.p0, c.p3) <= tolerance

    private fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 == 0.0) return p.distanceTo(a)
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0.0, 1.0)
        return p.distanceTo(Vec2(a.x + ab.x * t, a.y + ab.y * t))
    }

    private const val MAX_DEPTH = 16
}
