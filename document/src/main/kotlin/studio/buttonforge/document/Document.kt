package studio.buttonforge.document

import studio.buttonforge.document.export.ExportSettings
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.states.StateSet

data class CanvasSize(val width: Double, val height: Double) {
    init {
        require(width > 0.0 && height > 0.0 && width.isFinite() && height.isFinite()) { "Canvas size must be positive" }
    }
}

data class Document(
    val name: String,
    val canvas: CanvasSize,
    val layers: List<LayerNode> = emptyList(),
    val states: StateSet = StateSet(),
    val export: ExportSettings = ExportSettings(),
    val counters: IdCounters = IdCounters(),
)
