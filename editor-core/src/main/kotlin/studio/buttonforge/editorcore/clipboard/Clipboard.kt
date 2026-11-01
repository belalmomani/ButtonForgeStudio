package studio.buttonforge.editorcore.clipboard

import studio.buttonforge.document.layers.LayerNode

/** Holds one copied layer (or group). Shared across documents, so it survives opening another project. */
class Clipboard {
    var node: LayerNode? = null
        private set

    /** How many times the current content has been pasted; each paste lands a little further along. */
    var pastes: Int = 0
        private set

    fun put(node: LayerNode) {
        this.node = node
        pastes = 0
    }

    fun countPaste(): Int = ++pastes
}
