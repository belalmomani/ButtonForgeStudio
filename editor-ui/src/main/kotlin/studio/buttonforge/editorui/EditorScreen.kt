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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import studio.buttonforge.document.Document
import studio.buttonforge.editorcore.points.EditorState
import studio.buttonforge.editorcore.PointModeController
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.Viewport
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.editorcore.points.PointTool
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.editorcore.ContourReplacement
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CornerType
import studio.buttonforge.geometry.primitives.Primitives
import studio.buttonforge.renderer.SceneRenderer
import studio.buttonforge.document.appearance.Color as ModelColor
import studio.buttonforge.document.appearance.Stroke as ModelStroke

/** Shown on screen so it is clear which build is installed. */
private const val BUILD_TAG = "7أ"

private val POINT_COUNTS = listOf(25, 50, 100, 250, 500)
private val SIMPLIFY_TOLERANCES = listOf(0.5, 1.0, 2.0)

@Composable
fun EditorScreen(
    host: EditorHost,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    message: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { host.edit { it.undo() } }, enabled = host.undoCount > 0) { Text("تراجع") }
            OutlinedButton(onClick = { host.edit { it.redo() } }, enabled = host.redoCount > 0) { Text("إعادة") }
            Button(onClick = onSave) { Text("حفظ") }
            OutlinedButton(onClick = onOpen) { Text("فتح") }
            OutlinedButton(onClick = onNew) { Text("جديد") }
            Text("نسخة $BUILD_TAG", Modifier.padding(top = 12.dp))
        }
        EditorCanvas(host, Modifier.weight(1f).fillMaxWidth())
        if (message != null) Text(message, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        ControlPanel(host)
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

@Composable
private fun ControlPanel(host: EditorHost) {
    val doc = host.document
    val state = host.editorState
    val selected = state.selectedLayer
    val hasShape = flatten(doc.layers).any { it is ShapeLayer }
    val anchorCount = if (selected == null) 0 else
        (doc.findLayer(selected) as? ShapeLayer)?.shape?.geometry?.contours?.firstOrNull()?.anchors?.size ?: 0

    Column(
        Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // layers
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (node in flatten(doc.layers)) {
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
            ToggleButton("النقاط", state.mode == EditMode.Points, enabled = hasShape) { host.edit { it.enterPointModeAuto() } }
            if (state.mode == EditMode.Points) {
                ToggleButton("تحديد", state.pointTool == PointTool.Select) { host.edit { it.setPointTool(PointTool.Select) } }
                ToggleButton("إضافة", state.pointTool == PointTool.AddPoint) { host.edit { it.setPointTool(PointTool.AddPoint) } }
                ToggleButton("حذف", state.pointTool == PointTool.DeletePoint) { host.edit { it.setPointTool(PointTool.DeletePoint) } }
            }
        }
        if (state.mode == EditMode.Points && selected != null) {
            val ids = state.selectedAnchors
            if (ids.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نوع النقطة (${ids.size}):", Modifier.padding(top = 10.dp))
                    OutlinedButton(onClick = { host.edit { it.setCornerType(selected, ids, CornerType.Sharp) } }) { Text("حاد") }
                    OutlinedButton(onClick = { host.edit { it.setCornerType(selected, ids, CornerType.Smooth) } }) { Text("ناعم") }
                    OutlinedButton(onClick = { host.edit { it.setCornerType(selected, ids, CornerType.Symmetric) } }) { Text("متماثل") }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("النقاط: $anchorCount | إضافة بنفس الشكل:", Modifier.padding(top = 10.dp))
                for (n in POINT_COUNTS) {
                    OutlinedButton(onClick = { host.edit { it.addPointsToCount(selected, 0, n) } }, enabled = n > anchorCount) {
                        Text("$n")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("إعادة توزيع (تقريبي):", Modifier.padding(top = 10.dp))
                for (n in POINT_COUNTS) {
                    OutlinedButton(onClick = { host.preview { it.previewRecount(selected, 0, n) } }) { Text("$n") }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تبسيط (الانحراف الأقصى):", Modifier.padding(top = 10.dp))
                for (tol in SIMPLIFY_TOLERANCES) {
                    OutlinedButton(onClick = { host.preview { it.previewSimplify(selected, 0, tol) } }) { Text("$tol") }
                }
            }
            val pending = host.pending
            if (pending != null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "معاينة: ${pending.after.anchors.size} نقطة، أقصى انحراف ${"%.2f".format(pending.maxError)}",
                        Modifier.padding(top = 10.dp),
                    )
                    Button(onClick = { host.applyPending() }) { Text("تطبيق") }
                    OutlinedButton(onClick = { host.cancelPending() }) { Text("إلغاء") }
                }
            }
        }
        host.notice?.let { Text(it, Modifier.padding(top = 2.dp)) }
    }
}

private fun flatten(nodes: List<LayerNode>): List<LayerNode> =
    nodes.flatMap { n -> if (n is GroupLayer) listOf(n) + flatten(n.children) else listOf(n) }

private fun defaultLook() = Appearance(
    fills = listOf(Fill(Paint.Solid(ModelColor(0xFF3366FF.toInt())))),
    strokes = listOf(ModelStroke(Paint.Solid(ModelColor(0xFF1B2A6B.toInt())), width = 4.0)),
)

@Composable
private fun EditorCanvas(host: EditorHost, modifier: Modifier) {
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
                    val downScreen = Vec2(down.position.x.toDouble(), down.position.y.toDouble())
                    val downWorld = host.viewport.toWorld(downScreen)
                    val pointsMode = host.session.state.mode == EditMode.Points
                    var multiTouch = false
                    var moved = 0f
                    controller.handle(PointerEvent(PointerType.Down, downScreen))
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
                            if (pointsMode) {
                                controller.handle(
                                    PointerEvent(PointerType.Move, Vec2(change.position.x.toDouble(), change.position.y.toDouble())),
                                )
                            } else {
                                // Shape mode: a small movement is a tap, a larger one pans the view
                                val d = change.position - change.previousPosition
                                moved += d.getDistance()
                                if (moved > viewConfiguration.touchSlop) {
                                    host.transformViewport(1.0, 0.0, 0.0, d.x.toDouble(), d.y.toDouble())
                                }
                            }
                            if (change.positionChanged()) change.consume()
                        }
                        host.changed()
                    } while (event.changes.any { it.pressed })
                    if (!multiTouch) {
                        controller.handle(PointerEvent(PointerType.Up, Vec2.ZERO))
                        if (!pointsMode && moved <= viewConfiguration.touchSlop) {
                            host.edit { it.selectLayerAt(downWorld, touch.worldTolerance(host.viewport.scale)) }
                        }
                    }
                    host.changed()
                }
            },
    ) {
        val vp = host.viewport
        val document = host.document
        val editorState = host.editorState
        drawIntoCanvas { c ->
            SceneRenderer.draw(c.nativeCanvas, document, vp.scale.toFloat(), vp.offsetX.toFloat(), vp.offsetY.toFloat())
        }
        drawPointOverlay(document, editorState, vp, host.pending)
    }
}

/** Draws anchors and the handles of selected anchors. Visible size is independent of the touch target size. */
private fun DrawScope.drawPointOverlay(document: Document, state: EditorState, vp: Viewport, pending: ContourReplacement?) {
    if (state.mode != EditMode.Points) return
    val layerId = state.selectedLayer ?: return
    val layer = document.findLayer(layerId) as? ShapeLayer ?: return
    val t = layer.shape.transform

    fun screen(p: Vec2): Offset {
        val s = vp.toScreen(t.toWorld(p))
        return Offset(s.x.toFloat(), s.y.toFloat())
    }

    if (pending != null && pending.layerId == layerId) {
        val path = Path()
        val segments = pending.after.segments()
        if (segments.isNotEmpty()) {
            val first = screen(segments.first().p0)
            path.moveTo(first.x, first.y)
            for (c in segments) {
                val p1 = screen(c.p1)
                val p2 = screen(c.p2)
                val p3 = screen(c.p3)
                path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            drawPath(path, Color(0xFFD500F9), style = Stroke(width = 2.dp.toPx()))
        }
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
