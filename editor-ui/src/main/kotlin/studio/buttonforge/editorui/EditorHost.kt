package studio.buttonforge.editorui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import studio.buttonforge.document.Document
import studio.buttonforge.editorcore.ContourReplacement
import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.editorcore.draw.DrawTool
import studio.buttonforge.editorcore.draw.FreehandController
import studio.buttonforge.editorcore.draw.PenController
import studio.buttonforge.editorcore.draw.PenPoint
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.editorcore.input.Viewport
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.editorcore.points.EditorState

/**
 * Holds the editing session for Compose. The session is a plain mutable object, so every change goes through
 * [edit] or [changed], which bump [revision] so screens that read it redraw.
 */
@Stable
class EditorHost(initial: Document) {
    var session: EditorSession by mutableStateOf(EditorSession(initial))
        private set

    // Copies of the session's state held as Compose state. Screens read these, so they update on their own and
    // never depend on being re-invoked by a parent.
    var document: Document by mutableStateOf(initial)
        private set
    var editorState: EditorState by mutableStateOf(EditorState())
        private set
    var undoCount: Int by mutableIntStateOf(0)
        private set
    var redoCount: Int by mutableIntStateOf(0)
        private set
    var viewport: Viewport by mutableStateOf(Viewport())
        private set
    var revision: Int by mutableIntStateOf(0)
        private set

    // The view as a transform for the controllers. They read the current viewport on every event.
    private val liveViewport = object : ViewportTransform {
        override val scale: Double get() = viewport.scale
        override fun toWorld(screen: Vec2): Vec2 = viewport.toWorld(screen)
    }

    var freehand: FreehandController = FreehandController(session, liveViewport)
        private set
    var pen: PenController = PenController(session, liveViewport)
        private set

    /** Strokes being drawn, for the live preview. */
    var freehandPreview: List<Vec2> by mutableStateOf(emptyList())
        private set
    var penPreview: List<PenPoint> by mutableStateOf(emptyList())
        private set

    /** The selection rectangle being dragged, in world coordinates. */
    var marquee: Pair<Vec2, Vec2>? by mutableStateOf(null)
        private set

    fun setMarquee(rect: Pair<Vec2, Vec2>?) {
        if (marquee != rect) marquee = rect
    }

    /** A proposed re-pointing waiting for the user to accept or cancel it. */
    var pending: ContourReplacement? by mutableStateOf(null)
        private set

    /** A short message for the user (a refused operation, for example). */
    var notice: String? by mutableStateOf(null)
        private set

    private var viewWidth = 0
    private var viewHeight = 0
    private var fitted = false

    /** Call after anything changed the session. */
    fun changed() {
        publish()
    }

    fun <T> edit(block: (EditorSession) -> T): T {
        val result = block(session)
        publish()
        return result
    }

    fun replaceDocument(document: Document) {
        session = EditorSession(document)
        freehand = FreehandController(session, liveViewport)
        pen = PenController(session, liveViewport)
        fitted = false
        fitIfNeeded(viewWidth, viewHeight)
        publish()
    }

    /** Computes a preview without changing the document. A refused request becomes a notice. */
    fun preview(block: (EditorSession) -> ContourReplacement) {
        notice = null
        pending = try {
            block(session)
        } catch (e: IllegalArgumentException) {
            notice = e.message
            null
        }
    }

    fun applyPending() {
        val p = pending ?: return
        pending = null
        try {
            edit { it.applyReplacement(p) }
            notice = null
        } catch (e: IllegalStateException) {
            notice = "تغيّر المستند بعد المعاينة، أعد المعاينة"
        }
    }

    fun finishPen() {
        pen.finishOpen()
        publish()
    }

    fun cancelPen() {
        pen.cancel()
        publish()
    }

    fun cancelPending() {
        pending = null
    }

    fun showNotice(message: String?) {
        notice = message
    }

    private fun publish() {
        document = session.document
        editorState = session.state
        undoCount = session.history.undoSize
        redoCount = session.history.redoSize
        if (session.state.drawTool != DrawTool.Pen && pen.preview.isNotEmpty()) pen.cancel()
        if (session.state.drawTool != DrawTool.Freehand && freehand.preview.isNotEmpty()) freehand.cancel()
        freehandPreview = freehand.preview.toList()
        penPreview = pen.preview.toList()
        val p = pending
        if (p != null && (!session.isCurrent(p) || session.state.mode != EditMode.Points)) pending = null
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
