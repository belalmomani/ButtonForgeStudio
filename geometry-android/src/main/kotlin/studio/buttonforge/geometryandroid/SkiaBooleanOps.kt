package studio.buttonforge.geometryandroid

import android.graphics.Path
import android.graphics.PathMeasure
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.BooleanFailure
import studio.buttonforge.geometry.ops.BooleanOp
import studio.buttonforge.geometry.ops.BooleanOps
import studio.buttonforge.geometry.ops.BooleanOptions
import studio.buttonforge.geometry.ops.BooleanResult
import studio.buttonforge.geometry.ops.CurveRefit
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.FillRule
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Boolean operations through the platform's Path.op. The platform returns a path, not editable anchors, so each result
 * contour is sampled along its length and fitted back into Bézier anchors with [CurveRefit] (sharp corners are detected,
 * smooth parts become few anchors). The result therefore matches the exact outline to within the fit tolerance, but a
 * sharp corner can sit up to half a sampling step away from the exact vertex.
 *
 * Limits: closed fills only (no strokes); the result's anchors and ids are all new; the result uses the even-odd rule.
 */
class SkiaBooleanOps : BooleanOps {
    override fun apply(op: BooleanOp, a: CompoundPath, b: CompoundPath, options: BooleanOptions): BooleanResult {
        val nextId = options.nextId
            ?: return BooleanResult.Failure(BooleanFailure.Unsupported, "an anchor id source is required")
        if (options.isCancelled()) return BooleanResult.Failure(BooleanFailure.Cancelled)
        return try {
            val result = Path()
            val ok = result.op(
                toPath(a), toPath(b),
                when (op) {
                    BooleanOp.Union -> Path.Op.UNION
                    BooleanOp.Subtract -> Path.Op.DIFFERENCE
                    BooleanOp.Intersect -> Path.Op.INTERSECT
                    BooleanOp.Exclude -> Path.Op.XOR
                },
            )
            if (!ok) {
                BooleanResult.Failure(BooleanFailure.Internal, "Path.op reported failure")
            } else {
                extract(result, options, nextId)
            }
        } catch (e: RuntimeException) {
            BooleanResult.Failure(BooleanFailure.Internal, e.message.orEmpty())
        }
    }

    private fun toPath(geometry: CompoundPath): Path {
        val path = Path()
        path.fillType = if (geometry.fillRule == FillRule.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        for (contour in geometry.contours) {
            if (contour.anchors.size < 2) continue
            val first = contour.anchors.first().position
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (s in contour.segments()) {
                path.cubicTo(
                    s.p1.x.toFloat(), s.p1.y.toFloat(), s.p2.x.toFloat(), s.p2.y.toFloat(), s.p3.x.toFloat(), s.p3.y.toFloat(),
                )
            }
            if (contour.closed) path.close()
        }
        return path
    }

    private fun extract(path: Path, options: BooleanOptions, nextId: () -> studio.buttonforge.geometry.path.AnchorId): BooleanResult {
        val contours = ArrayList<Contour>()
        val notes = ArrayList<String>()
        val measure = PathMeasure(path, false)
        val tolerance = max(options.tolerance, MIN_FIT_TOLERANCE)
        do {
            val length = measure.length
            if (length < MIN_LENGTH) continue
            if (options.isCancelled()) return BooleanResult.Failure(BooleanFailure.Cancelled)
            val closed = measure.isClosed
            val spacing = (length / MAX_SAMPLES).coerceIn(MIN_SPACING, MAX_SPACING)
            val count = max(8, ceil(length / spacing).toInt())
            val points = ArrayList<Vec2>(count + 1)
            val pos = FloatArray(2)
            for (i in 0..count) {
                if (measure.getPosTan(min(length, length * i / count), pos, null)) {
                    points += Vec2(pos[0].toDouble(), pos[1].toDouble())
                }
            }
            if (closed && points.size > 1) points.removeAt(points.lastIndex) // the last sample is the first one again
            try {
                val fitted = CurveRefit.fit(points, closed, tolerance, 45.0, nextId, cornerWindow = 3)
                contours += fitted.contour
                if (!fitted.withinTolerance) notes += "a contour was fitted less closely than requested"
            } catch (e: IllegalArgumentException) {
                notes += "a degenerate contour was dropped"
            }
        } while (measure.nextContour())
        return BooleanResult.Success(CompoundPath(contours, FillRule.EvenOdd), notes)
    }

    private companion object {
        const val MIN_FIT_TOLERANCE = 0.05
        const val MIN_LENGTH = 1e-3f
        const val MAX_SAMPLES = 2000f
        const val MIN_SPACING = 0.1f
        const val MAX_SPACING = 2f
    }
}
