package studio.buttonforge.document.layers

import studio.buttonforge.document.shape.Shape

@JvmInline
value class LayerId(val value: Long)

sealed interface LayerNode {
    val id: LayerId
    val name: String
    val visible: Boolean
    val locked: Boolean
}

data class ShapeLayer(
    override val id: LayerId,
    override val name: String,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    val shape: Shape,
) : LayerNode

data class GroupLayer(
    override val id: LayerId,
    override val name: String,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    val children: List<LayerNode> = emptyList(),
) : LayerNode

fun LayerNode.subtreeIds(): Set<LayerId> = when (this) {
    is ShapeLayer -> setOf(id)
    is GroupLayer -> setOf(id) + children.flatMap { it.subtreeIds() }
}

fun LayerNode.withFlags(visible: Boolean = this.visible, locked: Boolean = this.locked): LayerNode = when (this) {
    is ShapeLayer -> copy(visible = visible, locked = locked)
    is GroupLayer -> copy(visible = visible, locked = locked)
}

fun LayerNode.renamed(newName: String): LayerNode = when (this) {
    is ShapeLayer -> copy(name = newName)
    is GroupLayer -> copy(name = newName)
}
