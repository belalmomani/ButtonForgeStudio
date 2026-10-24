package studio.buttonforge.document

import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.geometry.path.AnchorId

/** Structural checks used when loading files and in tests. Returns human-readable problems; empty means valid. */
object DocumentInvariants {
    fun check(doc: Document): List<String> {
        val problems = mutableListOf<String>()
        val layerIds = mutableListOf<LayerId>()
        val anchorIds = mutableListOf<AnchorId>()

        fun walk(nodes: List<LayerNode>) {
            for (n in nodes) {
                layerIds += n.id
                when (n) {
                    is ShapeLayer -> n.shape.geometry.contours.forEach { c -> c.anchors.forEach { anchorIds += it.id } }
                    is GroupLayer -> walk(n.children)
                }
            }
        }
        walk(doc.layers)

        layerIds.groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { problems += "Duplicate layer id ${it.value}" }
        anchorIds.groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { problems += "Duplicate anchor id ${it.value}" }
        layerIds.filter { it.value >= doc.counters.nextLayer }
            .forEach { problems += "Layer id ${it.value} is not below the layer counter ${doc.counters.nextLayer}" }
        anchorIds.filter { it.value >= doc.counters.nextAnchor }
            .forEach { problems += "Anchor id ${it.value} is not below the anchor counter ${doc.counters.nextAnchor}" }
        val known = layerIds.toSet()
        doc.states.overrides.values.flatMap { it.keys }.filter { it !in known }.toSet()
            .forEach { problems += "State override refers to missing layer ${it.value}" }
        return problems
    }
}
