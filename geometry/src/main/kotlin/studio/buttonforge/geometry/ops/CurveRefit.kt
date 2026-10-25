package studio.buttonforge.geometry.ops

import studio.buttonforge.geometry.bezier.CubicBezier
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** [maxError] is measured (distance from every input point to the result), not estimated by the fitter. */
data class RefitResult(val contour: Contour, val maxError: Double, val withinTolerance: Boolean)

/**
 * Operation 4: builds a new Bézier path from points. Unlike subdivision this CHANGES the shape, within a stated
 * tolerance. [fit] follows Schneider's least-squares algorithm (Graphics Gems, 1990) with corner detection.
 */
object CurveRefit {
    private const val MAX_TOLERANCE_HALVINGS = 8
    private const val DUPLICATE_DISTANCE = 1e-12

    /**
     * Fits cubic Béziers through [points] so that every point is within [tolerance] of the result. Points whose
     * direction changes by more than [cornerAngleDegrees] become Sharp anchors; all others are Smooth.
     * If the first attempt misses the tolerance the fitter retries with a tighter internal tolerance, up to 8 times;
     * [RefitResult.withinTolerance] says whether it succeeded.
     */
    fun fit(
        points: List<Vec2>,
        closed: Boolean,
        tolerance: Double,
        cornerAngleDegrees: Double = 45.0,
        nextId: () -> AnchorId,
    ): RefitResult {
        require(tolerance > 0.0 && tolerance.isFinite()) { "tolerance must be positive" }
        val pts = removeDuplicates(points, closed)
        require(pts.size >= 2) { "need at least 2 distinct points" }
        require(!closed || pts.size >= 3) { "a closed contour needs at least 3 distinct points" }

        var internal = tolerance
        var best: Pair<Fitted, Double>? = null
        repeat(MAX_TOLERANCE_HALVINGS) {
            val fitted = fitAll(pts, closed, internal, cornerAngleDegrees)
            val error = measure(pts, fitted.curves)
            val current = best
            if (current == null || error < current.second) best = fitted to error
            if (error <= tolerance) return build(fitted, closed, error, true, nextId)
            internal /= 2.0
        }
        val (fitted, error) = best!!
        return build(fitted, closed, error, false, nextId)
    }

    /**
     * A smooth curve that passes exactly through every point (Catmull-Rom converted to Béziers). Always yields
     * exactly points.size anchors, all Smooth; sharp corners in the data are rounded.
     */
    fun interpolate(points: List<Vec2>, closed: Boolean, nextId: () -> AnchorId): Contour {
        val n = points.size
        require(n >= 2) { "need at least 2 points" }
        require(!closed || n >= 3) { "a closed contour needs at least 3 points" }
        val anchors = List(n) { i ->
            val prev = if (closed) points[(i - 1 + n) % n] else points[max(i - 1, 0)]
            val next = if (closed) points[(i + 1) % n] else points[min(i + 1, n - 1)]
            val tangent = (next - prev) * (1.0 / 6.0)
            val hasIn = closed || i > 0
            val hasOut = closed || i < n - 1
            Anchor(
                nextId(), points[i],
                if (hasIn) tangent * -1.0 else Vec2.ZERO,
                if (hasOut) tangent else Vec2.ZERO,
                CornerType.Smooth,
            )
        }
        return Contour(anchors, closed)
    }

    // ---------------------------------------------------------------- fitting

    /** curves in order; startsWithCorner[k] says whether the anchor at the start of curve k is a corner. */
    private class Fitted(val curves: List<CubicBezier>, val startsWithCorner: List<Boolean>)

    private fun removeDuplicates(points: List<Vec2>, closed: Boolean): List<Vec2> {
        val out = ArrayList<Vec2>()
        for (p in points) if (out.isEmpty() || out.last().distanceTo(p) > DUPLICATE_DISTANCE) out += p
        if (closed && out.size > 1 && out.last().distanceTo(out.first()) <= DUPLICATE_DISTANCE) out.removeAt(out.lastIndex)
        return out
    }

    private fun fitAll(pts: List<Vec2>, closed: Boolean, tolerance: Double, cornerDeg: Double): Fitted {
        val n = pts.size
        val corners = findCorners(pts, closed, cornerDeg)
        val curves = ArrayList<CubicBezier>()
        val startsWithCorner = ArrayList<Boolean>()

        fun addRun(run: List<Vec2>, t1: Vec2, t2: Vec2, cornerAtStart: Boolean) {
            val runCurves = ArrayList<CubicBezier>()
            fitCubic(run, 0, run.size - 1, t1, t2, tolerance, runCurves)
            for ((j, c) in runCurves.withIndex()) {
                curves += c
                startsWithCorner += (j == 0 && cornerAtStart)
            }
        }

        if (!closed) {
            val cuts = listOf(0) + corners + listOf(n - 1)
            for (k in 0 until cuts.size - 1) {
                val run = pts.subList(cuts[k], cuts[k + 1] + 1)
                addRun(run, unit(run[1] - run[0]), unit(run[run.size - 2] - run.last()), cornerAtStart = true)
            }
        } else if (corners.isEmpty()) {
            val run = pts + pts[0]
            val tangent = unit(pts[1] - pts[n - 1])
            addRun(run, tangent, tangent * -1.0, cornerAtStart = false)
        } else {
            for (k in corners.indices) {
                val from = corners[k]
                val to = corners[(k + 1) % corners.size]
                val count = ((to - from + n) % n).let { if (it == 0) n else it }
                val run = List(count + 1) { pts[(from + it) % n] }
                addRun(run, unit(run[1] - run[0]), unit(run[run.size - 2] - run.last()), cornerAtStart = true)
            }
        }
        return Fitted(curves, startsWithCorner)
    }

    private fun findCorners(pts: List<Vec2>, closed: Boolean, cornerDeg: Double): List<Int> {
        val threshold = Math.toRadians(cornerDeg)
        val n = pts.size
        val range = if (closed) 0 until n else 1 until n - 1
        val result = ArrayList<Int>()
        for (i in range) {
            val a = unit(pts[i] - pts[(i - 1 + n) % n])
            val b = unit(pts[(i + 1) % n] - pts[i])
            val angle = acos(dot(a, b).coerceIn(-1.0, 1.0))
            if (angle > threshold) result += i
        }
        return result
    }

    private fun build(fitted: Fitted, closed: Boolean, error: Double, ok: Boolean, nextId: () -> AnchorId): RefitResult {
        val curves = fitted.curves
        val m = curves.size
        val anchorCount = if (closed) m else m + 1
        val anchors = List(anchorCount) { k ->
            val outCurve = if (k < m) curves[k] else null
            val inCurve = if (k > 0) curves[k - 1] else if (closed) curves[m - 1] else null
            val position = outCurve?.p0 ?: curves[m - 1].p3
            val corner = k == anchorCount - 1 && !closed || k == 0 && !closed || (k < m && fitted.startsWithCorner[k])
            Anchor(
                nextId(),
                position,
                if (inCurve != null) inCurve.p2 - inCurve.p3 else Vec2.ZERO,
                if (outCurve != null) outCurve.p1 - outCurve.p0 else Vec2.ZERO,
                if (corner) CornerType.Sharp else CornerType.Smooth,
            )
        }
        return RefitResult(Contour(anchors, closed), error, ok)
    }

    private fun measure(points: List<Vec2>, curves: List<CubicBezier>): Double {
        var worst = 0.0
        for (p in points) {
            var best = Double.MAX_VALUE
            for (c in curves) best = min(best, studio.buttonforge.geometry.bezier.CurveNearest.nearest(c, p).second)
            worst = max(worst, best)
        }
        return worst
    }

    // ---------------------------------------------------------------- Schneider core

    private fun fitCubic(d: List<Vec2>, first: Int, last: Int, tHat1: Vec2, tHat2: Vec2, error: Double, out: MutableList<CubicBezier>) {
        if (last - first + 1 == 2) {
            val dist = d[first].distanceTo(d[last]) / 3.0
            out += CubicBezier(d[first], d[first] + tHat1 * dist, d[last] + tHat2 * dist, d[last])
            return
        }
        var u = chordLengthParameters(d, first, last)
        var bezier = generateBezier(d, first, last, u, tHat1, tHat2)
        var (worst, split) = worstError(d, first, last, bezier, u)
        if (worst < error) {
            out += bezier
            return
        }
        if (worst < error * 4.0) {
            repeat(4) {
                val better = reparameterize(d, first, last, u, bezier)
                bezier = generateBezier(d, first, last, better, tHat1, tHat2)
                val (e, s) = worstError(d, first, last, bezier, better)
                if (e < error) {
                    out += bezier
                    return
                }
                u = better
                worst = e
                split = s
            }
        }
        val center = unit((d[split - 1] - d[split]) + (d[split] - d[split + 1]))
        fitCubic(d, first, split, tHat1, center, error, out)
        fitCubic(d, split, last, center * -1.0, tHat2, error, out)
    }

    private fun chordLengthParameters(d: List<Vec2>, first: Int, last: Int): DoubleArray {
        val u = DoubleArray(last - first + 1)
        for (i in 1 until u.size) u[i] = u[i - 1] + d[first + i].distanceTo(d[first + i - 1])
        val total = u.last()
        if (total > 0.0) for (i in u.indices) u[i] /= total
        return u
    }

    private fun generateBezier(d: List<Vec2>, first: Int, last: Int, u: DoubleArray, tHat1: Vec2, tHat2: Vec2): CubicBezier {
        var c00 = 0.0
        var c01 = 0.0
        var c11 = 0.0
        var x0 = 0.0
        var x1 = 0.0
        val p0 = d[first]
        val p3 = d[last]
        for (i in u.indices) {
            val t = u[i]
            val a1 = tHat1 * b1(t)
            val a2 = tHat2 * b2(t)
            c00 += dot(a1, a1)
            c01 += dot(a1, a2)
            c11 += dot(a2, a2)
            val tmp = d[first + i] - (p0 * (b0(t) + b1(t)) + p3 * (b2(t) + b3(t)))
            x0 += dot(a1, tmp)
            x1 += dot(a2, tmp)
        }
        val det = c00 * c11 - c01 * c01
        val alphaL = if (det == 0.0) 0.0 else (x0 * c11 - x1 * c01) / det
        val alphaR = if (det == 0.0) 0.0 else (c00 * x1 - c01 * x0) / det
        val segLength = p0.distanceTo(p3)
        val epsilon = 1e-6 * segLength
        return if (alphaL < epsilon || alphaR < epsilon) {
            val dist = segLength / 3.0
            CubicBezier(p0, p0 + tHat1 * dist, p3 + tHat2 * dist, p3)
        } else {
            CubicBezier(p0, p0 + tHat1 * alphaL, p3 + tHat2 * alphaR, p3)
        }
    }

    /** Returns (largest distance at the parametrised points, index of that point; always strictly inside the range). */
    private fun worstError(d: List<Vec2>, first: Int, last: Int, bezier: CubicBezier, u: DoubleArray): Pair<Double, Int> {
        var split = (last + first + 1) / 2
        var worst = 0.0
        for (i in first + 1 until last) {
            val dist = bezier.at(u[i - first]).distanceTo(d[i])
            if (dist >= worst) {
                worst = dist
                split = i
            }
        }
        return worst to split
    }

    private fun reparameterize(d: List<Vec2>, first: Int, last: Int, u: DoubleArray, bezier: CubicBezier): DoubleArray =
        DoubleArray(u.size) { i -> newton(bezier, d[first + i], u[i]) }

    private fun newton(c: CubicBezier, p: Vec2, u: Double): Double {
        val q = c.at(u)
        val q1 = c.derivative(u)
        val v = 1.0 - u
        val q2 = ((c.p2 - c.p1 * 2.0 + c.p0) * v + (c.p3 - c.p2 * 2.0 + c.p1) * u) * 6.0
        val numerator = dot(q - p, q1)
        val denominator = dot(q1, q1) + dot(q - p, q2)
        if (denominator == 0.0) return u
        return (u - numerator / denominator).coerceIn(0.0, 1.0)
    }

    private fun b0(u: Double) = (1.0 - u).pow(3)
    private fun b1(u: Double) = 3.0 * u * (1.0 - u).pow(2)
    private fun b2(u: Double) = 3.0 * u * u * (1.0 - u)
    private fun b3(u: Double) = u * u * u
    private fun dot(a: Vec2, b: Vec2) = a.x * b.x + a.y * b.y
    private fun unit(v: Vec2): Vec2 {
        val l = v.length()
        return if (l == 0.0) v else Vec2(v.x / l, v.y / l)
    }
}
