package studio.buttonforge.editorui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.appearance.Fill
import studio.buttonforge.document.appearance.Paint
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.editorcore.PointModeController
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.Viewport
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.editorcore.points.PointTool
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.primitives.Primitives
import studio.buttonforge.renderer.SceneRenderer
import studio.buttonforge.document.appearance.Color as ModelColor
import studio.buttonforge.document.appearance.Stroke as ModelStroke

private val POINT_COUNTS = listOf(25, 50, 100, 250, 500)

@Composable
fun EditorScreen(
    host: EditorHost,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    message: String?,
    modifier: Modifier = Modifier,
) {
    // Read here so this screen recomposes after every edit, and passed down so the children below are not skipped:
    // the session is a plain object, so Compose cannot see changes inside it.
    val revision = host.revision
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { host.edit { it.undo() } }, enabled = host.session.history.undoSize > 0) { Text("تراجع") }
            OutlinedButton(onClick = { host.edit { it.redo() } }, enabled = host.session.history.redoSize > 0) { Text("إعادة") }
            Button(onClick = onSave) { Text("حفظ") }
            OutlinedButton(onClick = onOpen) { Text("فتح") }
            OutlinedButton(onClick = onNew) { Text("جديد") }
        }
        EditorCanvas(host, revision, Modifier.weight(1f).fillMaxWidth())
        if (message != null) Text(message, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        ControlPanel(host, revision)
    }
}

@Composable
private fun ToggleButton(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(text) }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun ControlPanel(host: EditorHost, revision: Int) {
    val session = host.session
    val state = session.state
    val selected = state.selectedLayer
    val shapeSelected = selected != null && session.document.findLayer(selected) is ShapeLayer
    val anchorCount = if (selected == null) 0 else
        (session.document.findLayer(selected) as? ShapeLayer)?.shape?.geometry?.contours?.firstOrNull()?.anchors?.size ?: 0

    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // layers
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (node in flatten(session.document.layers)) {
                ToggleButton(node.name, node.id == selected) { host.edit { it.selectLayer(node.id) } }
            }
        }
        // add shapes
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val look = defaultLook()
            OutlinedButton(onClick = {
                host.edit { it.addShapeLayer("دائرة", look) { next -> Primitives.circle(Vec2(256.0, 256.0), 100.0, next) } }
            }) { Text("+ دائرة") }
            OutlinedButton(onClick = {
                host.edit { it.addShapeLayer("مستطيل", look) { next -> Primitives.rectangle(Vec2(256.0, 256.0), 240.0, 140.0, next) } }
            }) { Text("+ مستطيل") }
            OutlinedButton(onClick = {
                host.edit { it.addShapeLayer("نجمة", look) { next -> Primitives.star(Vec2(256.0, 256.0), 120.0, 50.0, 5, next) } }
            }) { Text("+ نجمة") }
        }
        // mode and tools
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleButton("الشكل", state.mode == EditMode.Shape) { host.edit { it.exitPointMode() } }
            ToggleButton("النقاط", state.mode == EditMode.Points, enabled = shapeSelected) { host.edit { it.enterPointMode() } }
            if (state.mode == EditMode.Points) {
                ToggleButton("تحديد", state.pointTool == PointTool.Select) { host.edit { it.setPointTool(PointTool.Select) } }
                ToggleButton("إضافة", state.pointTool == PointTool.AddPoint) { host.edit { it.setPointTool(PointTool.AddPoint) } }
                ToggleButton("حذف", state.pointTool == PointTool.DeletePoint) { host.edit { it.setPointTool(PointTool.DeletePoint) } }
            }
        }
        // point count
        if (state.mode == EditMode.Points && selected != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("النقاط: $anchorCount", Modifier.padding(top = 10.dp))
                for (n in POINT_COUNTS) {
                    OutlinedButton(onClick = { host.edit { it.addPointsToCount(selected, 0, n) } }, enabled = n > anchorCount) {
                        Text("$n")
                    }
                }
            }
        }
    }
}

private fun flatten(nodes: List<LayerNode>): List<LayerNode> =
    nodes.flatMap { n -> if (n is GroupLayer) listOf(n) + flatten(n.children) else listOf(n) }

private fun defaultLook() = Appearance(
    fills = listOf(Fill(Paint.Solid(ModelColor(0xFF3366FF.toInt())))),
    strokes = listOf(ModelStroke(Paint.Solid(ModelColor(0xFF1B2A6B.toInt())), width = 4.0)),
)

@Suppress("UNUSED_PARAMETER")
@Composable
private fun EditorCanvas(host: EditorHost, revision: Int, modifier: Modifier) {
    val density = LocalDensity.current
    val touch = remember(density) { TouchSettings(hitRadiusPx = with(density) { 24.dp.toPx() }.toDouble()) }
    val session = host.session
    val liveViewport = remember(host) {
        object : studio.buttonforge.editorcore.input.ViewportTransform {
            override val scale: Double get() = host.viewport.scale
            override fun toWorld(screen: Vec2): Vec2 = host.viewport.toWorld(screen)
        }
    }
    val controller = remember(session, touch) { PointModeController(session, liveViewport, touch) }

    Canvas(
        modifier
            .background(Color(0xFFE0E0E0))
            .onSizeChanged { host.onViewSize(it.width, it.height) }
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var multiTouch = false
                    controller.handle(PointerEvent(PointerType.Down, Vec2(down.position.x.toDouble(), down.position.y.toDouble())))
                    host.changed()
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            if (!multiTouch) {
                                multiTouch = true
                                controller.handle(PointerEvent(PointerType.Cancel, Vec2.ZERO))
                            }
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            host.transformViewport(
                                zoom.toDouble(), centroid.x.toDouble(), centroid.y.toDouble(),
                                pan.x.toDouble(), pan.y.toDouble(),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!multiTouch && pressed.size == 1) {
                            val change = pressed.first()
                            if (host.session.state.mode == EditMode.Points) {
                                controller.handle(
                                    PointerEvent(PointerType.Move, Vec2(change.position.x.toDouble(), change.position.y.toDouble())),
                                )
                            } else {
                                // in Shape mode one finger pans the view
                                val d = change.position - change.previousPosition
                                host.transformViewport(1.0, 0.0, 0.0, d.x.toDouble(), d.y.toDouble())
                            }
                            if (change.positionChanged()) change.consume()
                        }
                        host.changed()
                    } while (event.changes.any { it.pressed })
                    if (!multiTouch) controller.handle(PointerEvent(PointerType.Up, Vec2.ZERO))
                    host.changed()
                }
            },
    ) {
        // Reading the revision inside the draw block makes every edit redraw the canvas.
        check(host.revision >= 0)
        val vp = host.viewport
        drawIntoCanvas { c ->
            SceneRenderer.draw(c.nativeCanvas, host.session.document, vp.scale.toFloat(), vp.offsetX.toFloat(), vp.offsetY.toFloat())
        }
        drawPointOverlay(host.session, vp)
    }
}

/** Draws anchors and the handles of selected anchors. Visible size is independent of the touch target size. */
private fun DrawScope.drawPointOverlay(session: EditorSession, vp: Viewport) {
    val state = session.state
    if (state.mode != EditMode.Points) return
    val layerId = state.selectedLayer ?: return
    val layer = session.document.findLayer(layerId) as? ShapeLayer ?: return
    val t = layer.shape.transform

    fun screen(p: Vec2): Offset {
        val s = vp.toScreen(t.toWorld(p))
        return Offset(s.x.toFloat(), s.y.toFloat())
    }

    val radius = 5.dp.toPx()
    val line = Stroke(width = 1.5.dp.toPx())
    for (contour in layer.shape.geometry.contours) {
        for (anchor in contour.anchors) {
            if (anchor.id !in state.selectedAnchors) continue
            val at = screen(anchor.position)
            for (handle in listOf(anchor.handleIn, anchor.handleOut)) {
                if (handle == Vec2.ZERO) continue
                val h = screen(anchor.position + handle)
                drawLine(Color(0xFFFF6D00), at, h, strokeWidth = 1.5.dp.toPx())
                drawCircle(Color(0xFFFF6D00), radius * 0.7f, h)
            }
        }
        for (anchor in contour.anchors) {
            val at = screen(anchor.position)
            if (anchor.id in state.selectedAnchors) {
                drawCircle(Color(0xFFFF6D00), radius * 1.4f, at)
            } else {
                drawCircle(Color.White, radius, at)
                drawCircle(Color(0xFF1565C0), radius, at, style = line)
            }
        }
    }
}
