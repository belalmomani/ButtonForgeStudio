package studio.buttonforge.editorui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import studio.buttonforge.document.Document
import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.editorcore.input.Viewport

/**
 * Holds the editing session for Compose. The session is a plain mutable object, so every change goes through
 * [edit] or [changed], which bump [revision] so screens that read it redraw.
 */
@Stable
class EditorHost(document: Document) {
    var session: EditorSession by mutableStateOf(EditorSession(document))
        private set
    var viewport: Viewport by mutableStateOf(Viewport())
        private set
    var revision: Int by mutableIntStateOf(0)
        private set

    private var viewWidth = 0
    private var viewHeight = 0
    private var fitted = false

    fun changed() {
        revision++
    }

    fun <T> edit(block: (EditorSession) -> T): T {
        val result = block(session)
        revision++
        return result
    }

    fun replaceDocument(document: Document) {
        session = EditorSession(document)
        fitted = false
        fitIfNeeded(viewWidth, viewHeight)
        revision++
    }

    fun onViewSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        fitIfNeeded(width, height)
    }

    private fun fitIfNeeded(width: Int, height: Int) {
        if (fitted || width <= 0 || height <= 0) return
        val canvas = session.document.canvas
        viewport = Viewport.fit(canvas.width, canvas.height, width.toDouble(), height.toDouble())
        fitted = true
    }

    fun transformViewport(zoom: Double, centroidX: Double, centroidY: Double, panX: Double, panY: Double) {
        viewport = viewport.zoomedAbout(centroidX, centroidY, zoom).panned(panX, panY)
    }
}
