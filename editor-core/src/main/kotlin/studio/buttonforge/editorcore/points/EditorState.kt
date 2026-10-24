package studio.buttonforge.editorcore.points

import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.geometry.path.AnchorId

/** Shape mode selects whole shapes. Points mode edits the anchors of the selected shape. */
enum class EditMode { Shape, Points }

enum class PointTool { Select, AddPoint, DeletePoint }

data class EditorState(
    val selectedLayer: LayerId? = null,
    val mode: EditMode = EditMode.Shape,
    val selectedAnchors: Set<AnchorId> = emptySet(),
    val pointTool: PointTool = PointTool.Select,
)

/**
 * Touch target size is a pure interaction setting, separate from how big a point is drawn.
 * It is expressed in screen pixels and converted to world units with the zoom level.
 */
data class TouchSettings(val hitRadiusPx: Double = 24.0) {
    init {
        require(hitRadiusPx > 0.0 && hitRadiusPx.isFinite()) { "hitRadiusPx must be positive" }
    }

    fun worldTolerance(zoom: Double): Double {
        require(zoom > 0.0) { "zoom must be positive" }
        return hitRadiusPx / zoom
    }
}
