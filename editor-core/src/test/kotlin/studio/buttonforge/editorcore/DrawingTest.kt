package studio.buttonforge.editorcore

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.editorcore.draw.DrawTool
import studio.buttonforge.editorcore.draw.FreehandController
import studio.buttonforge.editorcore.draw.PenController
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.ops.PathError
import studio.buttonforge.geometry.path.Contour
import studio.buttonforge.geometry.path.CornerType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DrawingTest {
    private class View(override val scale: Double = 1.0) : ViewportTransform {
        override fun toWorld(screen: Vec2) = Vec2(screen.x / scale, screen.y / scale)
    }

    private fun session(tool: DrawTool): EditorSession {
        val s = EditorSession(Document("d", CanvasSize(1000.0, 1000.0)))
        s.setDrawTool(tool)
        return s
    }

    private fun FreehandController.stroke(points: List<Vec2>) {
        handle(PointerEvent(PointerType.Down, points.first()))
        for (p in points.drop(1)) handle(PointerEvent(PointerType.Move, p))
        handle(PointerEvent(PointerType.Up, Vec2.ZERO))
    }

    private fun PenController.tap(p: Vec2) {
        handle(PointerEvent(PointerType.Down, p))
        handle(PointerEvent(PointerType.Up, Vec2.ZERO))
    }

    private fun PenController.drag(p: Vec2, to: Vec2) {
        handle(PointerEvent(PointerType.Down, p))
        handle(PointerEvent(PointerType.Move, to))
        handle(PointerEvent(PointerType.Up, Vec2.ZERO))
    }

    private fun contourOf(s: EditorSession): Contour = (s.document.layers.last() as ShapeLayer).shape.geometry.contours.single()

    @Test
    fun aShakyCircleBecomesAFewSmoothAnchorsOnAClosedShape() {
        val s = session(DrawTool.Freehand)
        val c = FreehandController(s, View())
        val rnd = Random(3)
        val stroke = List(121) { i ->
            val a = 2 * Math.PI * i / 120
            Vec2(300 + 100 * cos(a) + rnd.nextDouble(-0.5, 0.5), 300 + 100 * sin(a) + rnd.nextDouble(-0.5, 0.5))
        }
        c.stroke(stroke)

        assertEquals(1, s.document.layers.size)
        val contour = contourOf(s)
        assertTrue(contour.closed)
        assertTrue(contour.anchors.size in 3..20, "anchors=${contour.anchors.size}")
        assertTrue(contour.anchors.none { it.corner == CornerType.Sharp }, "no false corners from the shaking")
        val reference = List(200) { i -> Vec2(300 + 100 * cos(2 * Math.PI * i / 200), 300 + 100 * sin(2 * Math.PI * i / 200)) }
        assertTrue(PathError.maxDistance(reference, contour) <= 6.0)
        assertEquals(emptyList(), DocumentInvariants.check(s.document))
        assertEquals(DrawTool.Freehand, s.state.drawTool, "the tool stays active for the next stroke")
        assertEquals(1, s.history.undoSize)
        assertTrue(s.undo())
        assertEquals(0, s.document.layers.size)
    }

    @Test
    fun anOpenStrokeStaysOpenAndKeepsItsCorner() {
        val s = session(DrawTool.Freehand)
        val c = FreehandController(s, View())
        val corner = Vec2(200.0, 200.0)
        val stroke = List(41) { Vec2(corner.x - 200 + it * 5.0, corner.y) } + List(40) { Vec2(corner.x, corner.y + (it + 1) * 5.0) }
        c.stroke(stroke)
        val contour = contourOf(s)
        assertFalse(contour.closed)
        assertTrue(contour.anchors.any { it.corner == CornerType.Sharp && it.position.distanceTo(corner) < 8.0 })
    }

    @Test
    fun aTapOrAToolThatIsOffDrawsNothing() {
        val s = session(DrawTool.Freehand)
        val c = FreehandController(s, View())
        c.stroke(listOf(Vec2(10.0, 10.0), Vec2(11.0, 10.0)))
        assertEquals(0, s.document.layers.size)

        s.setDrawTool(DrawTool.None)
        c.stroke(List(30) { Vec2(it * 10.0, 5.0) })
        assertEquals(0, s.document.layers.size)
        assertTrue(c.preview.isEmpty())
    }

    @Test
    fun zoomDoesNotChangeHowAStrokeIsInterpreted() {
        val s = session(DrawTool.Freehand)
        val c = FreehandController(s, View(scale = 4.0))
        // 4 pixels per world unit: a 400px stroke is a 100-unit stroke
        c.stroke(List(81) { Vec2(it * 5.0, 100.0 + if (it % 2 == 0) 1.0 else -1.0) })
        val contour = contourOf(s)
        assertEquals(2, contour.anchors.size)
        assertTrue(contour.anchors.last().position.x in 95.0..101.0)
    }

    @Test
    fun theLivePreviewFollowsTheFinger() {
        val s = session(DrawTool.Freehand)
        val c = FreehandController(s, View())
        c.handle(PointerEvent(PointerType.Down, Vec2(0.0, 0.0)))
        c.handle(PointerEvent(PointerType.Move, Vec2(1.0, 0.0))) // under the 3px step: ignored
        c.handle(PointerEvent(PointerType.Move, Vec2(10.0, 0.0)))
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0)), c.preview)
        c.handle(PointerEvent(PointerType.Cancel, Vec2.ZERO))
        assertTrue(c.preview.isEmpty())
    }

    @Test
    fun penTapsMakeCornersAndTappingTheFirstPointClosesTheShape() {
        val s = session(DrawTool.Pen)
        val pen = PenController(s, View())
        pen.tap(Vec2(100.0, 100.0))
        pen.tap(Vec2(300.0, 100.0))
        pen.tap(Vec2(200.0, 300.0))
        assertEquals(3, pen.preview.size)
        pen.tap(Vec2(104.0, 98.0)) // near the first point
        assertTrue(pen.preview.isEmpty())

        val contour = contourOf(s)
        assertTrue(contour.closed)
        assertEquals(3, contour.anchors.size)
        assertTrue(contour.anchors.all { it.corner == CornerType.Sharp })
        assertEquals(Vec2(100.0, 100.0), contour.anchors[0].position)
        assertEquals(1, s.history.undoSize)
    }

    @Test
    fun penDragsPullSmoothHandlesAndAnOpenPathCanBeFinished() {
        val s = session(DrawTool.Pen)
        val pen = PenController(s, View())
        pen.tap(Vec2(0.0, 0.0))
        pen.drag(Vec2(100.0, 0.0), Vec2(140.0, 0.0))
        pen.tap(Vec2(200.0, 100.0))
        assertTrue(pen.finishOpen())

        val c = contourOf(s)
        assertFalse(c.closed)
        assertEquals(3, c.anchors.size)
        val mid = c.anchors[1]
        assertEquals(CornerType.Symmetric, mid.corner)
        assertEquals(Vec2(40.0, 0.0), mid.handleOut)
        assertEquals(Vec2(-40.0, 0.0), mid.handleIn)
        assertEquals(Vec2.ZERO, c.anchors[0].handleIn)
        assertEquals(Vec2.ZERO, c.anchors[2].handleOut)
    }

    @Test
    fun aTinyDragIsStillACornerAndATooShortPathIsKept() {
        val s = session(DrawTool.Pen)
        val pen = PenController(s, View())
        pen.drag(Vec2(0.0, 0.0), Vec2(3.0, 0.0)) // under the 8px drag threshold
        assertEquals(Vec2.ZERO, pen.preview.single().handleOut)
        assertFalse(pen.finishOpen())
        assertEquals(1, pen.preview.size, "points are kept so the user can continue")
        assertEquals(0, s.document.layers.size)
    }

    @Test
    fun aSecondFingerRemovesTheUncommittedPoint() {
        val s = session(DrawTool.Pen)
        val pen = PenController(s, View())
        pen.tap(Vec2(0.0, 0.0))
        pen.handle(PointerEvent(PointerType.Down, Vec2(50.0, 50.0)))
        assertEquals(2, pen.preview.size)
        pen.handle(PointerEvent(PointerType.Cancel, Vec2.ZERO))
        assertEquals(1, pen.preview.size)
    }

    @Test
    fun theToolSwitchClearsDrawingStateAndLeavesPointMode() {
        val s = EditorSession(Document("d", CanvasSize(100.0, 100.0)))
        s.addShapeLayer("c", studio.buttonforge.document.appearance.Appearance()) { next ->
            studio.buttonforge.geometry.primitives.Primitives.circle(Vec2(50.0, 50.0), 20.0, next)
        }
        s.enterPointMode()
        s.setDrawTool(DrawTool.Pen)
        assertEquals(studio.buttonforge.editorcore.points.EditMode.Shape, s.state.mode)
        assertEquals(emptySet(), s.state.selectedAnchors)
        assertTrue(s.enterPointMode())
        assertEquals(DrawTool.None, s.state.drawTool)
        assertNotNull(s.state.selectedLayer)
    }
}
