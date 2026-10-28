package studio.buttonforge.editorcore.commands

import studio.buttonforge.document.Document
import studio.buttonforge.document.IdCounters
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.document.states.StateSet
import studio.buttonforge.editorcore.history.Command
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.Contour
import kotlin.math.max

internal fun CompoundPath.mapAnchors(f: (Anchor) -> Anchor): CompoundPath =
    copy(contours = contours.map { c -> c.copy(anchors = c.anchors.map(f)) })

/** Counters never go backwards, so an id is never handed out twice even after undo. */
internal fun IdCounters.atLeast(other: IdCounters) =
    IdCounters(max(nextAnchor, other.nextAnchor), max(nextLayer, other.nextLayer))

data class AnchorMove(val id: AnchorId, val from: Vec2, val to: Vec2)

/**
 * Moving points: stores only the moved anchors' old and new positions. Nudges (same [mergeKey], same points, one
 * starting where the last ended, within the merge window) collapse into a single history entry.
 */
class MoveAnchorsCommand(
    val layerId: LayerId,
    val moves: List<AnchorMove>,
    val mergeKey: String? = null,
    val timeMillis: Long = 0L,
) : Command {
    override val label: String = "Move points"
    override val cost: Int get() = moves.size

    override fun apply(doc: Document): Document = place(doc) { it.to }
    override fun revert(doc: Document): Document = place(doc) { it.from }

    override fun mergeWith(next: Command): Command? {
        if (mergeKey == null || next !is MoveAnchorsCommand) return null
        if (next.mergeKey != mergeKey || next.layerId != layerId) return null
        if (next.timeMillis - timeMillis > MERGE_WINDOW_MILLIS) return null
        if (next.moves.map { it.id } != moves.map { it.id }) return null
        val ends = moves.associate { it.id to it.to }
        if (next.moves.any { ends[it.id] != it.from }) return null
        val finals = next.moves.associate { it.id to it.to }
        return MoveAnchorsCommand(layerId, moves.map { AnchorMove(it.id, it.from, finals.getValue(it.id)) }, mergeKey, next.timeMillis)
    }

    private fun place(doc: Document, pick: (AnchorMove) -> Vec2): Document {
        val target = moves.associate { it.id to pick(it) }
        return doc.updateShape(layerId) { shape ->
            shape.copy(
                geometry = shape.geometry.mapAnchors { a ->
                    val p = target[a.id]
                    if (p == null) a else a.copy(position = p)
                },
            )
        }
    }
}

/** Structural edits (insert, delete, add-to-count): swaps one contour. One history entry per operation. */
class ReplaceContourCommand(
    val layerId: LayerId,
    val contourIndex: Int,
    val before: Contour,
    val after: Contour,
    val countersAfter: IdCounters,
    override val label: String,
) : Command {
    override val cost: Int get() = before.anchors.size + after.anchors.size

    override fun apply(doc: Document): Document {
        val updated = replace(doc, after)
        return updated.copy(counters = updated.counters.atLeast(countersAfter))
    }

    override fun revert(doc: Document): Document = replace(doc, before) // counters stay advanced

    private fun replace(doc: Document, contour: Contour): Document = doc.updateShape(layerId) { shape ->
        val contours = shape.geometry.contours.toMutableList()
        contours[contourIndex] = contour
        shape.copy(geometry = shape.geometry.copy(contours = contours))
    }
}

const val MERGE_WINDOW_MILLIS = 500L

/** Appearance changes; consecutive changes with the same merge key within the window become one entry. */
class SetAppearanceCommand(
    val layerId: LayerId,
    val before: Appearance,
    val after: Appearance,
    val mergeKey: String?,
    val timeMillis: Long,
) : Command {
    override val label: String = "Change appearance"

    override fun apply(doc: Document): Document = doc.updateShape(layerId) { it.copy(appearance = after) }
    override fun revert(doc: Document): Document = doc.updateShape(layerId) { it.copy(appearance = before) }

    override fun mergeWith(next: Command): Command? {
        if (mergeKey == null || next !is SetAppearanceCommand) return null
        if (next.mergeKey != mergeKey || next.layerId != layerId) return null
        if (next.timeMillis - timeMillis > MERGE_WINDOW_MILLIS) return null
        return SetAppearanceCommand(layerId, before, next.after, mergeKey, next.timeMillis)
    }
}

/** Layer add/remove/move/group: keeps the before and after layer lists (shared structure, so cheap). */
class SetLayersCommand(
    private val beforeLayers: List<LayerNode>,
    private val afterLayers: List<LayerNode>,
    private val beforeStates: StateSet,
    private val afterStates: StateSet,
    private val countersAfter: IdCounters,
    override val label: String,
) : Command {
    override fun apply(doc: Document): Document =
        doc.copy(layers = afterLayers, states = afterStates, counters = doc.counters.atLeast(countersAfter))

    override fun revert(doc: Document): Document = doc.copy(layers = beforeLayers, states = beforeStates)
}

/** One anchor's handles and corner type (its position and id do not change). Used for handle drags. */
class SetAnchorCommand(val layerId: LayerId, val before: Anchor, val after: Anchor) : Command {
    init {
        require(before.id == after.id) { "SetAnchorCommand cannot change an anchor's id" }
    }

    override val label: String = "Edit handle"

    override fun apply(doc: Document): Document = place(doc, after)
    override fun revert(doc: Document): Document = place(doc, before)

    private fun place(doc: Document, target: Anchor): Document = doc.updateShape(layerId) { shape ->
        shape.copy(geometry = shape.geometry.mapAnchors { a -> if (a.id == target.id) target else a })
    }
}

/** Several commands as one undo step. */
class CompositeCommand(override val label: String, private val parts: List<Command>) : Command {
    override val cost: Int get() = parts.sumOf { it.cost }
    override fun apply(doc: Document): Document = parts.fold(doc) { d, c -> c.apply(d) }
    override fun revert(doc: Document): Document = parts.asReversed().fold(doc) { d, c -> c.revert(d) }
}
