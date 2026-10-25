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
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.HandleSide
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
    private var dragKind = DragKind.None

    private enum class DragKind { None, Anchors, Handle }

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
                // handles of the selected anchors come first: they can sit close to other anchors
                val handle = nearestHandle(geometry, st.selectedAnchors, local, tolerance)
                if (handle != null) {
                    if (session.beginHandleDrag(layerId, handle.first, handle.second)) {
                        dragOrigin = world
                        dragKind = DragKind.Handle
                    }
                    return
                }
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
                if (session.beginAnchorDrag(layerId, ids)) {
                    dragOrigin = world
                    dragKind = DragKind.Anchors
                }
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

    private fun nearestHandle(
        geometry: CompoundPath,
        selected: Set<AnchorId>,
        local: Vec2,
        tolerance: Double,
    ): Pair<AnchorId, HandleSide>? {
        var best: Pair<AnchorId, HandleSide>? = null
        var bestDistance = tolerance
        for (contour in geometry.contours) {
            for (anchor in contour.anchors) {
                if (anchor.id !in selected) continue
                for (side in HandleSide.values()) {
                    val handle = if (side == HandleSide.In) anchor.handleIn else anchor.handleOut
                    if (handle == Vec2.ZERO) continue
                    val d = (anchor.position + handle).distanceTo(local)
                    if (d <= bestDistance) {
                        bestDistance = d
                        best = anchor.id to side
                    }
                }
            }
        }
        return best
    }

    private fun onMove(screen: Vec2) {
        val origin = dragOrigin ?: return
        val delta = viewport.toWorld(screen) - origin
        when (dragKind) {
            DragKind.Anchors -> session.updateAnchorDrag(delta)
            DragKind.Handle -> session.updateHandleDrag(delta)
            DragKind.None -> Unit
        }
    }

    private fun onUp() {
        if (dragOrigin == null) return
        dragOrigin = null
        when (dragKind) {
            DragKind.Anchors -> session.commitAnchorDrag()
            DragKind.Handle -> session.commitHandleDrag()
            DragKind.None -> false
        }
        dragKind = DragKind.None
    }

    private fun onCancel() {
        if (dragOrigin == null) return
        dragOrigin = null
        dragKind = DragKind.None
        session.cancelAnchorDrag()
    }
}
