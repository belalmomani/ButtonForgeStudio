package studio.buttonforge.editorcore

import studio.buttonforge.document.Document
import studio.buttonforge.document.IdAllocator
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.addLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.editorcore.commands.AnchorMove
import studio.buttonforge.editorcore.commands.MoveAnchorsCommand
import studio.buttonforge.editorcore.commands.ReplaceContourCommand
import studio.buttonforge.editorcore.commands.SetAppearanceCommand
import studio.buttonforge.editorcore.commands.SetLayersCommand
import studio.buttonforge.editorcore.history.Command
import studio.buttonforge.editorcore.history.CommandHistory
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.editorcore.points.EditorState
import studio.buttonforge.editorcore.points.PointTool
import studio.buttonforge.geometry.hittest.HitTester
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.ArcLengthDistribution
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.SegmentParam
import studio.buttonforge.geometry.path.withInsertedAnchors

/**
 * The editing core: current document, selection state and history. Synchronous and free of Android and
 * coroutines; the UI decides which thread calls it.
 */
class EditorSession(initial: Document, val history: CommandHistory = CommandHistory()) {
    var document: Document = initial
        private set
    var state: EditorState = EditorState()
        private set

    private class AnchorDrag(
        val layerId: LayerId,
        val base: Document,
        val starts: Map<AnchorId, Vec2>,
        val transform: Transform,
    ) {
        var moves: List<AnchorMove>? = null
    }

    private var drag: AnchorDrag? = null

    // ---------- history ----------

    fun execute(command: Command) {
        cancelAnchorDrag()
        document = command.apply(document)
        history.record(command)
        normalizeState()
    }

    fun undo(): Boolean {
        cancelAnchorDrag()
        val c = history.popUndo() ?: return false
        document = c.revert(document)
        normalizeState()
        return true
    }

    fun redo(): Boolean {
        cancelAnchorDrag()
        val c = history.popRedo() ?: return false
        document = c.apply(document)
        normalizeState()
        return true
    }

    // ---------- dragging points: one history entry per drag ----------

    fun beginAnchorDrag(layerId: LayerId, ids: Set<AnchorId>): Boolean {
        cancelAnchorDrag()
        val layer = document.findLayer(layerId) as? ShapeLayer ?: return false
        val starts = layer.shape.geometry.contours.flatMap { it.anchors }
            .filter { it.id in ids }
            .associate { it.id to it.position }
        if (starts.isEmpty()) return false
        drag = AnchorDrag(layerId, document, starts, layer.shape.transform)
        return true
    }

    /** [worldDelta] is the total displacement since the drag began, in world units. Not recorded in history. */
    fun updateAnchorDrag(worldDelta: Vec2) {
        val d = drag ?: return
        val local = d.transform.vectorToLocal(worldDelta) ?: return
        val moves = d.starts.map { (id, p) -> AnchorMove(id, p, p + local) }
        d.moves = moves
        document = MoveAnchorsCommand(d.layerId, moves).apply(d.base)
    }

    /** Records the drag as a single command. Returns false (and records nothing) if nothing moved. */
    fun commitAnchorDrag(): Boolean {
        val d = drag ?: return false
        drag = null
        val moves = d.moves
        if (moves == null || moves.all { it.from == it.to }) {
            document = d.base
            return false
        }
        history.record(MoveAnchorsCommand(d.layerId, moves))
        normalizeState()
        return true
    }

    fun cancelAnchorDrag() {
        val d = drag ?: return
        drag = null
        document = d.base
    }

    val isDragging: Boolean get() = drag != null

    // ---------- point editing ----------

    /** Adds anchors so the contour has exactly [target] anchors, spaced evenly by arc length. Shape is unchanged. */
    fun addPointsToCount(layerId: LayerId, contourIndex: Int, target: Int): Boolean {
        val layer = document.findLayer(layerId) as? ShapeLayer ?: throw IllegalArgumentException("Not a shape layer")
        val contour = layer.shape.geometry.contours.getOrNull(contourIndex)
            ?: throw IllegalArgumentException("No contour $contourIndex")
        val params = ArcLengthDistribution.fillTo(contour, target)
        if (params.isEmpty()) return false
        val alloc = IdAllocator(document.counters)
        val after = contour.withInsertedAnchors(params, alloc::nextAnchorId)
        execute(ReplaceContourCommand(layerId, contourIndex, contour, after, alloc.counters(), "Add points"))
        return true
    }

    /** Inserts one anchor on a segment without changing the shape. Returns its id, or null at a segment end. */
    fun insertAnchorAt(layerId: LayerId, contourIndex: Int, segmentIndex: Int, t: Double): AnchorId? {
        if (t <= END_MARGIN || t >= 1.0 - END_MARGIN) return null
        val layer = document.findLayer(layerId) as? ShapeLayer ?: return null
        val contour = layer.shape.geometry.contours.getOrNull(contourIndex) ?: return null
        if (segmentIndex !in 0 until contour.segmentCount) return null
        val alloc = IdAllocator(document.counters)
        val after = contour.withInsertedAnchors(listOf(SegmentParam(segmentIndex, t)), alloc::nextAnchorId)
        val newId = AnchorId(alloc.counters().nextAnchor - 1)
        execute(ReplaceContourCommand(layerId, contourIndex, contour, after, alloc.counters(), "Add point"))
        return newId
    }

    /** Deletes an anchor and lets the shape change (free delete). Refuses to leave fewer than 2 anchors. */
    fun deleteAnchor(layerId: LayerId, anchorId: AnchorId): Boolean {
        val layer = document.findLayer(layerId) as? ShapeLayer ?: return false
        val contours = layer.shape.geometry.contours
        val ci = contours.indexOfFirst { c -> c.anchors.any { it.id == anchorId } }
        if (ci < 0) return false
        val contour = contours[ci]
        if (contour.anchors.size <= 2) return false
        val after = contour.copy(anchors = contour.anchors.filter { it.id != anchorId })
        execute(ReplaceContourCommand(layerId, ci, contour, after, document.counters, "Delete point"))
        return true
    }

    // ---------- appearance and layers ----------

    fun setAppearance(layerId: LayerId, appearance: Appearance, mergeKey: String? = null, nowMillis: Long = 0L) {
        val layer = document.findLayer(layerId) as? ShapeLayer ?: throw IllegalArgumentException("Not a shape layer")
        if (layer.shape.appearance == appearance) return
        execute(SetAppearanceCommand(layerId, layer.shape.appearance, appearance, mergeKey, nowMillis))
    }

    /** Runs a layer operation (add, remove, move, group...) as one undoable step. */
    fun updateLayers(label: String, f: (Document) -> Document) {
        val before = document
        val after = f(before)
        if (after.layers === before.layers && after.states === before.states) return
        if (after.layers == before.layers && after.states == before.states) return
        execute(SetLayersCommand(before.layers, after.layers, before.states, after.states, after.counters, label))
    }

    /** Creates a shape layer on top, selects it, and makes it one undoable step. [build] receives an anchor id source. */
    fun addShapeLayer(name: String, appearance: Appearance, build: (() -> AnchorId) -> CompoundPath): LayerId {
        val alloc = IdAllocator(document.counters)
        val geometry = build(alloc::nextAnchorId)
        val id = alloc.nextLayerId()
        val node = ShapeLayer(id, name, shape = Shape(geometry, appearance))
        val counters = alloc.counters()
        updateLayers("Add shape") { it.addLayer(node).copy(counters = counters) }
        selectLayer(id)
        return id
    }

    // ---------- selection and modes ----------

    fun selectLayer(id: LayerId?) {
        state = if (id == null || document.findLayer(id) == null) {
            EditorState(pointTool = state.pointTool)
        } else {
            EditorState(selectedLayer = id, pointTool = state.pointTool)
        }
    }

    fun enterPointMode(): Boolean {
        val id = state.selectedLayer ?: return false
        if (document.findLayer(id) !is ShapeLayer) return false
        state = state.copy(mode = EditMode.Points)
        return true
    }

    /**
     * Enters Points mode on the selected shape; if none is selected, selects the topmost shape first.
     * Returns false only when the document has no shape at all.
     */
    fun enterPointModeAuto(): Boolean {
        val current = state.selectedLayer?.let { document.findLayer(it) }
        if (current !is ShapeLayer) {
            val top = lastShape(document.layers) ?: return false
            selectLayer(top.id)
        }
        return enterPointMode()
    }

    /** Selects the topmost visible, unlocked shape under [world] (inside it or near its outline), or clears the selection. */
    fun selectLayerAt(world: Vec2, worldTolerance: Double): LayerId? {
        val hit = shapeAt(document.layers, world, worldTolerance)
        selectLayer(hit?.id)
        return hit?.id
    }

    private fun lastShape(nodes: List<LayerNode>): ShapeLayer? {
        for (n in nodes.asReversed()) {
            when (n) {
                is ShapeLayer -> return n
                is GroupLayer -> lastShape(n.children)?.let { return it }
            }
        }
        return null
    }

    private fun shapeAt(nodes: List<LayerNode>, world: Vec2, worldTolerance: Double): ShapeLayer? {
        for (n in nodes.asReversed()) {
            if (!n.visible || n.locked) continue
            when (n) {
                is GroupLayer -> shapeAt(n.children, world, worldTolerance)?.let { return it }
                is ShapeLayer -> {
                    val t = n.shape.transform
                    val local = t.toLocal(world) ?: continue
                    val scale = (kotlin.math.abs(t.scale.x) + kotlin.math.abs(t.scale.y)) / 2.0
                    val geometry = n.shape.geometry
                    if (HitTester.contains(geometry, local) || HitTester.nearestSegment(geometry, local, worldTolerance / scale) != null) {
                        return n
                    }
                }
            }
        }
        return null
    }

    fun exitPointMode() {
        state = state.copy(mode = EditMode.Shape, selectedAnchors = emptySet())
    }

    /** Tapping empty space: in Shape mode it deselects the shape, in Points mode only the points. */
    fun tapEmpty() {
        state = when (state.mode) {
            EditMode.Shape -> state.copy(selectedLayer = null, selectedAnchors = emptySet())
            EditMode.Points -> state.copy(selectedAnchors = emptySet())
        }
    }

    fun selectAnchors(ids: Set<AnchorId>, additive: Boolean) {
        check(state.mode == EditMode.Points) { "Points can only be selected in Points mode" }
        state = state.copy(selectedAnchors = if (additive) state.selectedAnchors + ids else ids)
    }

    fun setPointTool(tool: PointTool) {
        state = state.copy(pointTool = tool)
    }

    /** Drops selection that no longer exists after an edit, undo or redo. */
    private fun normalizeState() {
        val id = state.selectedLayer ?: return
        val node = document.findLayer(id)
        if (node == null) {
            state = EditorState(pointTool = state.pointTool)
            return
        }
        if (node !is ShapeLayer) {
            state = state.copy(mode = EditMode.Shape, selectedAnchors = emptySet())
            return
        }
        val existing = node.shape.geometry.contours.flatMap { c -> c.anchors.map { it.id } }.toSet()
        val kept = state.selectedAnchors.filter { it in existing }.toSet()
        if (kept != state.selectedAnchors) state = state.copy(selectedAnchors = kept)
    }

    private companion object {
        const val END_MARGIN = 1e-6
    }
}
