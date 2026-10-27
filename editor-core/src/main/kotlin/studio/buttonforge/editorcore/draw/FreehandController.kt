package studio.buttonforge.editorcore.draw

import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.Resampling

/**
 * Collects a finger stroke and, when the finger lifts, creates a path. The stroke is smoothed and re-spaced before
 * fitting so shaky input does not create false corners. A stroke that ends near where it began becomes a closed shape.
 * Sizes are in screen pixels, so the result does not depend on the zoom level.
 */
class FreehandController(
    private val session: EditorSession,
    private val viewport: ViewportTransform,
    private val minStepPx: Double = 3.0,
    private val fitTolerancePx: Double = 4.0,
    private val spacingPx: Double = 6.0,
    private val closeDistancePx: Double = 24.0,
    private val minLengthPx: Double = 12.0,
) {
    private val points = ArrayList<Vec2>()

    /** The stroke so far, in world coordinates, for drawing a live preview. */
    val preview: List<Vec2> get() = points

    fun cancel() = points.clear()

    fun handle(event: PointerEvent) {
        if (session.state.drawTool != DrawTool.Freehand) {
            points.clear()
            return
        }
        when (event.type) {
            PointerType.Down -> {
                points.clear()
                points += viewport.toWorld(event.screen)
            }
            PointerType.Move -> {
                if (points.isEmpty()) return
                val w = viewport.toWorld(event.screen)
                if (w.distanceTo(points.last()) * viewport.scale >= minStepPx) points += w
            }
            PointerType.Up -> finish()
            PointerType.Cancel -> points.clear()
        }
    }

    private fun finish() {
        val raw = points.toList()
        points.clear()
        val scale = viewport.scale
        val length = raw.zipWithNext { a, b -> a.distanceTo(b) }.sum() * scale
        if (raw.size < 2 || length < minLengthPx) return

        val closed = raw.size >= 6 && length >= 4 * closeDistancePx && raw.first().distanceTo(raw.last()) * scale <= closeDistancePx
        val spacing = spacingPx / scale
        var pts = Resampling.resamplePolyline(Resampling.smoothPolyline(raw, 2), spacing)
        if (closed && pts.size > 3 && pts.last().distanceTo(pts.first()) < spacing * 1.5) pts = pts.dropLast(1)
        session.addDrawnPath(pts, closed, fitTolerancePx / scale)
    }
}
