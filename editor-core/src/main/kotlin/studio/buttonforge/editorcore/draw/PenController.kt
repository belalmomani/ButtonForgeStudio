package studio.buttonforge.editorcore.draw

import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.geometry.math.Vec2

/**
 * Pen tool: each tap adds a corner point, a press-and-drag adds a smooth point with a handle. Tapping near the first
 * point (with at least 3 points) closes the shape; [finishOpen] ends an open path.
 */
class PenController(
    private val session: EditorSession,
    private val viewport: ViewportTransform,
    private val closeDistancePx: Double = 24.0,
    private val dragThresholdPx: Double = 8.0,
) {
    private val points = ArrayList<PenPoint>()
    private var anchorWorld: Vec2? = null
    private var closing = false

    val preview: List<PenPoint> get() = points

    fun cancel() {
        points.clear()
        anchorWorld = null
        closing = false
    }

    /** Ends an open path. Returns false (keeping the points) if there are fewer than 2. */
    fun finishOpen(): Boolean {
        if (points.size < 2) return false
        val result = session.addPenPath(points.toList(), closed = false)
        cancel()
        return result != null
    }

    fun handle(event: PointerEvent) {
        if (session.state.drawTool != DrawTool.Pen) {
            cancel()
            return
        }
        when (event.type) {
            PointerType.Down -> onDown(viewport.toWorld(event.screen))
            PointerType.Move -> onMove(viewport.toWorld(event.screen))
            PointerType.Up -> onUp()
            PointerType.Cancel -> onCancel()
        }
    }

    private fun onDown(world: Vec2) {
        if (points.size >= 3 && points.first().position.distanceTo(world) * viewport.scale <= closeDistancePx) {
            closing = true
            return
        }
        anchorWorld = world
        points += PenPoint(world)
    }

    private fun onMove(world: Vec2) {
        val origin = anchorWorld ?: return
        val drag = world - origin
        points[points.lastIndex] =
            if (drag.length() * viewport.scale >= dragThresholdPx) PenPoint(origin, drag) else PenPoint(origin)
    }

    private fun onUp() {
        anchorWorld = null
        if (closing) {
            closing = false
            session.addPenPath(points.toList(), closed = true)
            points.clear()
        }
    }

    /** A second finger arrived: forget the point that this touch had just added. */
    private fun onCancel() {
        if (anchorWorld != null && points.isNotEmpty()) points.removeAt(points.lastIndex)
        anchorWorld = null
        closing = false
    }
}
