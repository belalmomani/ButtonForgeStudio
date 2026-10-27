package studio.buttonforge.geometry.ops

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.math.lerp
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

    /** Moving average over 2 * [radius] + 1 neighbours; the first and last points stay where they are. */
    fun smoothPolyline(points: List<Vec2>, radius: Int): List<Vec2> {
        require(radius >= 0) { "radius must not be negative" }
        if (points.size < 3 || radius == 0) return points
        return points.indices.map { i ->
            if (i == 0 || i == points.lastIndex) {
                points[i]
            } else {
                val from = maxOf(0, i - radius)
                val to = minOf(points.lastIndex, i + radius)
                var x = 0.0
                var y = 0.0
                for (j in from..to) {
                    x += points[j].x
                    y += points[j].y
                }
                val n = (to - from + 1).toDouble()
                Vec2(x / n, y / n)
            }
        }
    }

    /** Points along a polyline every [spacing] units (by length), always including the first and the last point. */
    fun resamplePolyline(points: List<Vec2>, spacing: Double): List<Vec2> {
        require(spacing > 0.0 && spacing.isFinite()) { "spacing must be positive" }
        if (points.size < 2) return points
        val out = arrayListOf(points[0])
        var carried = 0.0
        var previous = points[0]
        for (i in 1 until points.size) {
            var a = previous
            val b = points[i]
            var length = a.distanceTo(b)
            while (length > 0.0 && carried + length >= spacing) {
                val need = spacing - carried
                val p = lerp(a, b, need / length)
                out += p
                a = p
                length = a.distanceTo(b)
                carried = 0.0
            }
            carried += length
            previous = b
        }
        if (out.last().distanceTo(points.last()) > 1e-9) out += points.last()
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
