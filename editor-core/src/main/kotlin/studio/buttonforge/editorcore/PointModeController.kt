package studio.buttonforge.editorcore

import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.editorcore.points.PointTool
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.geometry.hittest.HitTester
import studio.buttonforge.geometry.math.Vec2
import kotlin.math.abs

/** Turns pointer events into point edits. Ignores events unless the session is in Points mode. */
class PointModeController(
    private val session: EditorSession,
    private val viewport: ViewportTransform,
    private val touch: TouchSettings = TouchSettings(),
) {
    /** UI toggle: tapping an anchor adds it to the selection instead of replacing it. */
    var additiveSelection: Boolean = false

    private var dragOrigin: Vec2? = null

    fun handle(event: PointerEvent) {
        when (event.type) {
            PointerType.Down -> onDown(event.screen)
            PointerType.Move -> onMove(event.screen)
            PointerType.Up -> onUp()
            PointerType.Cancel -> onCancel()
        }
    }

    private fun onDown(screen: Vec2) {
        val st = session.state
        if (st.mode != EditMode.Points) return
        val layerId = st.selectedLayer ?: return
        val layer = session.document.findLayer(layerId) as? ShapeLayer ?: return
        val transform = layer.shape.transform
        val world = viewport.toWorld(screen)
        val local = transform.toLocal(world) ?: return
        val scale = (abs(transform.scale.x) + abs(transform.scale.y)) / 2.0
        val tolerance = touch.worldTolerance(viewport.scale) / scale
        val geometry = layer.shape.geometry

        when (st.pointTool) {
            PointTool.Select -> {
                val hit = HitTester.nearestAnchor(geometry, local, tolerance)
                if (hit == null) {
                    session.tapEmpty()
                    return
                }
                val ids = if (hit.id in st.selectedAnchors) {
                    st.selectedAnchors
                } else {
                    session.selectAnchors(setOf(hit.id), additiveSelection)
                    session.state.selectedAnchors
                }
                if (session.beginAnchorDrag(layerId, ids)) dragOrigin = world
            }
            PointTool.AddPoint -> {
                if (HitTester.nearestAnchor(geometry, local, tolerance) != null) return
                val seg = HitTester.nearestSegment(geometry, local, tolerance) ?: return
                val newId = session.insertAnchorAt(layerId, seg.contourIndex, seg.segmentIndex, seg.t)
                if (newId != null) session.selectAnchors(setOf(newId), false)
            }
            PointTool.DeletePoint -> {
                val hit = HitTester.nearestAnchor(geometry, local, tolerance) ?: return
                session.deleteAnchor(layerId, hit.id)
            }
        }
    }

    private fun onMove(screen: Vec2) {
        val origin = dragOrigin ?: return
        session.updateAnchorDrag(viewport.toWorld(screen) - origin)
    }

    private fun onUp() {
        if (dragOrigin == null) return
        dragOrigin = null
        session.commitAnchorDrag()
    }

    private fun onCancel() {
        if (dragOrigin == null) return
        dragOrigin = null
        session.cancelAnchorDrag()
    }
}
