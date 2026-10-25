package studio.buttonforge.geometry.hittest

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.FillRule
import kotlin.math.max
import kotlin.math.min

/** Linear-scan hit testing. A spatial index is added only if measurements show it is needed. */
object HitTester {
    data class AnchorHit(val contourIndex: Int, val anchorIndex: Int, val id: AnchorId, val distance: Double)
    data class SegmentHit(val contourIndex: Int, val segmentIndex: Int, val t: Double, val distance: Double)

    private const val COARSE = 24

    /** Nearest anchor within [tolerance] (same units as the path). Ties go to the lower id. */
    fun nearestAnchor(path: CompoundPath, p: Vec2, tolerance: Double): AnchorHit? {
        var best: AnchorHit? = null
        for ((ci, contour) in path.contours.withIndex()) {
            for ((ai, anchor) in contour.anchors.withIndex()) {
                val d = anchor.position.distanceTo(p)
                if (d > tolerance) continue
                val b = best
                if (b == null || d < b.distance || (d == b.distance && anchor.id.value < b.id.value)) {
                    best = AnchorHit(ci, ai, anchor.id, d)
                }
            }
        }
        return best
    }

    /** Nearest point on any segment within [tolerance]. */
    fun nearestSegment(path: CompoundPath, p: Vec2, tolerance: Double): SegmentHit? {
        var best: SegmentHit? = null
        for ((ci, contour) in path.contours.withIndex()) {
            for (si in 0 until contour.segmentCount) {
                val (t, d) = nearestOnCurve(contour.segment(si), p)
                if (d > tolerance) continue
                val b = best
                if (b == null || d < b.distance) best = SegmentHit(ci, si, t, d)
            }
        }
        return best
    }

    private const val FLATTEN = 24

    /**
     * Whether [p] is inside the filled area, using the path's own fill rule. Curves are flattened into 24 straight
     * pieces per segment, so points within a hair of the outline may be classified either way. Open contours are
     * treated as closed, as a fill does.
     */
    fun contains(path: CompoundPath, p: Vec2): Boolean {
        var winding = 0
        var crossings = 0
        for (contour in path.contours) {
            if (contour.segmentCount == 0) continue
            val points = ArrayList<Vec2>()
            for (segment in contour.segments()) {
                for (i in 0 until FLATTEN) points += segment.at(i.toDouble() / FLATTEN)
            }
            points += contour.segment(contour.segmentCount - 1).p3
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                val side = (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)
                if (a.y <= p.y) {
                    if (b.y > p.y && side > 0) winding++
                } else {
                    if (b.y <= p.y && side < 0) winding--
                }
                if ((a.y > p.y) != (b.y > p.y)) {
                    val x = a.x + (p.y - a.y) / (b.y - a.y) * (b.x - a.x)
                    if (x > p.x) crossings++
                }
            }
        }
        return if (path.fillRule == FillRule.NonZero) winding != 0 else crossings % 2 == 1
    }

    private fun nearestOnCurve(c: CubicBezier, p: Vec2): Pair<Double, Double> {
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
