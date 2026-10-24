package studio.buttonforge.document.layers

import studio.buttonforge.document.Document
import studio.buttonforge.document.shape.Shape

private data class Location(val parent: LayerId?, val index: Int)

fun List<LayerNode>.findLayer(id: LayerId): LayerNode? {
    for (n in this) {
        if (n.id == id) return n
        if (n is GroupLayer) {
            val inner = n.children.findLayer(id)
            if (inner != null) return inner
        }
    }
    return null
}

fun Document.findLayer(id: LayerId): LayerNode? = layers.findLayer(id)

private fun locate(list: List<LayerNode>, id: LayerId, parent: LayerId?): Location? {
    for ((i, n) in list.withIndex()) {
        if (n.id == id) return Location(parent, i)
        if (n is GroupLayer) {
            val inner = locate(n.children, id, n.id)
            if (inner != null) return inner
        }
    }
    return null
}

/** Replaces the node with f(node), or removes it when f returns null. Returns null if the id is not found. */
private fun replaceNode(list: List<LayerNode>, id: LayerId, f: (LayerNode) -> LayerNode?): List<LayerNode>? {
    for ((i, n) in list.withIndex()) {
        if (n.id == id) {
            val replacement = f(n)
            val copy = list.toMutableList()
            if (replacement == null) {
                copy.removeAt(i)
            } else {
                copy[i] = replacement
            }
            return copy
        }
        if (n is GroupLayer) {
            val newChildren = replaceNode(n.children, id, f)
            if (newChildren != null) {
                val copy = list.toMutableList()
                copy[i] = n.copy(children = newChildren)
                return copy
            }
        }
    }
    return null
}

private fun removeRaw(layers: List<LayerNode>, id: LayerId): List<LayerNode> =
    replaceNode(layers, id) { null } ?: throw IllegalArgumentException("Layer ${id.value} not found")

private fun insertRaw(layers: List<LayerNode>, node: LayerNode, parent: LayerId?, index: Int?): List<LayerNode> {
    if (parent == null) {
        val i = index ?: layers.size
        require(i in 0..layers.size) { "Index $i out of range" }
        val copy = layers.toMutableList()
        copy.add(i, node)
        return copy
    }
    return replaceNode(layers, parent) { target ->
        require(target is GroupLayer) { "Parent ${parent.value} is not a group" }
        val i = index ?: target.children.size
        require(i in 0..target.children.size) { "Index $i out of range" }
        val children = target.children.toMutableList()
        children.add(i, node)
        target.copy(children = children)
    } ?: throw IllegalArgumentException("Parent ${parent.value} not found")
}

fun Document.addLayer(node: LayerNode, parent: LayerId? = null, index: Int? = null): Document {
    require(node.subtreeIds().none { findLayer(it) != null }) { "Duplicate layer id" }
    return copy(layers = insertRaw(layers, node, parent, index))
}

fun Document.removeLayer(id: LayerId): Document {
    val node = findLayer(id) ?: throw IllegalArgumentException("Layer ${id.value} not found")
    return copy(layers = removeRaw(layers, id), states = states.withoutLayers(node.subtreeIds()))
}

/** [index] is the position in the destination list after the layer has been taken out. */
fun Document.moveLayer(id: LayerId, newParent: LayerId?, index: Int? = null): Document {
    val node = findLayer(id) ?: throw IllegalArgumentException("Layer ${id.value} not found")
    require(newParent == null || newParent !in node.subtreeIds()) { "Cannot move a layer into itself or its descendants" }
    val without = removeRaw(layers, id)
    return copy(layers = insertRaw(without, node, newParent, index))
}

fun Document.updateLayer(id: LayerId, f: (LayerNode) -> LayerNode): Document {
    val updated = replaceNode(layers, id) { old ->
        val new = f(old)
        require(new.id == old.id) { "A layer update must keep the layer id" }
        new
    } ?: throw IllegalArgumentException("Layer ${id.value} not found")
    return copy(layers = updated)
}

fun Document.setVisible(id: LayerId, visible: Boolean): Document = updateLayer(id) { it.withFlags(visible = visible) }

fun Document.setLocked(id: LayerId, locked: Boolean): Document = updateLayer(id) { it.withFlags(locked = locked) }

fun Document.renameLayer(id: LayerId, name: String): Document = updateLayer(id) { it.renamed(name) }

fun Document.updateShape(id: LayerId, f: (Shape) -> Shape): Document = updateLayer(id) { node ->
    require(node is ShapeLayer) { "Layer ${id.value} is not a shape layer" }
    node.copy(shape = f(node.shape))
}

/** Groups sibling layers. The group takes the position of the lowest-indexed member; relative order is kept. */
fun Document.groupLayers(ids: List<LayerId>, newGroupId: LayerId, name: String = "Group"): Document {
    require(ids.isNotEmpty()) { "Nothing to group" }
    require(ids.toSet().size == ids.size) { "Duplicate ids" }
    require(findLayer(newGroupId) == null) { "Duplicate layer id" }
    val located = ids.map { id ->
        id to (locate(layers, id, null) ?: throw IllegalArgumentException("Layer ${id.value} not found"))
    }
    require(located.map { it.second.parent }.toSet().size == 1) { "Layers must share the same parent" }
    val ordered = located.sortedBy { it.second.index }
    val parent = ordered.first().second.parent
    val insertAt = ordered.first().second.index
    val nodes = ordered.map { (id, _) -> findLayer(id)!! }
    var remaining = layers
    for ((id, _) in ordered) remaining = removeRaw(remaining, id)
    val group = GroupLayer(newGroupId, name, children = nodes)
    return copy(layers = insertRaw(remaining, group, parent, insertAt))
}

fun Document.ungroup(groupId: LayerId): Document {
    val loc = locate(layers, groupId, null) ?: throw IllegalArgumentException("Layer ${groupId.value} not found")
    val node = findLayer(groupId)
    require(node is GroupLayer) { "Layer ${groupId.value} is not a group" }
    var result = removeRaw(layers, groupId)
    node.children.forEachIndexed { k, child -> result = insertRaw(result, child, loc.parent, loc.index + k) }
    return copy(layers = result, states = states.withoutLayers(setOf(groupId)))
}
