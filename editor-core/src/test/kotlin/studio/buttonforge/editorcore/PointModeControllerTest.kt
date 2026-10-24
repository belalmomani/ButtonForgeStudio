package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.editorcore.points.PointTool
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointModeControllerTest {
    private class Viewport(override val scale: Double = 1.0) : ViewportTransform {
        override fun toWorld(screen: Vec2) = Vec2(screen.x / scale, screen.y / scale)
    }

    private val layer = LayerId(1)

    private fun contour(s: EditorSession): Contour =
        (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.first()

    private fun setup(scale: Double = 1.0, radiusPx: Double = 24.0): Pair<EditorSession, PointModeController> {
        val s = EditorSession(SampleDocuments.circleButton())
        s.selectLayer(layer)
        s.enterPointMode()
        return s to PointModeController(s, Viewport(scale), TouchSettings(radiusPx))
    }

    private fun PointModeController.down(x: Double, y: Double) = handle(PointerEvent(PointerType.Down, Vec2(x, y)))
    private fun PointModeController.move(x: Double, y: Double) = handle(PointerEvent(PointerType.Move, Vec2(x, y)))
    private fun PointModeController.up() = handle(PointerEvent(PointerType.Up, Vec2.ZERO))

    @Test
    fun touchingNearAPointSelectsItAndDraggingMovesItAsOneEntry() {
        val (s, c) = setup()
        c.down(101.0, 1.0) // a little off the anchor at (100, 0)
        assertEquals(setOf(AnchorId(1)), s.state.selectedAnchors)
        for (x in 102..111) c.move(x.toDouble(), 1.0)
        c.up()
        assertEquals(1, s.history.undoSize)
        val moved = contour(s).anchors.first { it.id == AnchorId(1) }
        assertEquals(110.0, moved.position.x, 1e-9)
        assertEquals(0.0, moved.position.y, 1e-9)
    }

    @Test
    fun touchingEmptySpaceClearsPointsButKeepsTheShapeAndMode() {
        val (s, c) = setup()
        c.down(101.0, 1.0)
        c.up()
        c.down(0.0, 0.0)
        assertEquals(emptySet(), s.state.selectedAnchors)
        assertEquals(layer, s.state.selectedLayer)
        assertEquals(EditMode.Points, s.state.mode)
    }

    @Test
    fun zoomShrinksTheWorldSizeOfTheTouchTarget() {
        // radius 24 px at zoom 4 is 6 world units: 10 units away misses, at zoom 1 it hits
        val (s4, c4) = setup(scale = 4.0)
        c4.down(110.0 * 4.0, 0.0)
        assertEquals(emptySet(), s4.state.selectedAnchors)
        val (s1, c1) = setup(scale = 1.0)
        c1.down(110.0, 0.0)
        assertEquals(setOf(AnchorId(1)), s1.state.selectedAnchors)
    }

    @Test
    fun touchRadiusNeverChangesGeometry() {
        for (radius in listOf(1.0, 24.0, 500.0)) {
            val (s, c) = setup(radiusPx = radius)
            val before = s.document
            c.down(101.0, 1.0)
            c.up()
            assertEquals(before, s.document, "radius=$radius")
            assertEquals(0, s.history.undoSize)
        }
        assertEquals(6.0, TouchSettings(24.0).worldTolerance(4.0), 1e-12)
    }

    @Test
    fun addPointToolInsertsAPointOnTheCurveAndSelectsIt() {
        val (s, c) = setup()
        s.setPointTool(PointTool.AddPoint)
        c.down(70.71, 70.71) // on the arc between the first two anchors
        assertEquals(5, contour(s).anchors.size)
        assertEquals(1, s.state.selectedAnchors.size)
        assertEquals(1, s.history.undoSize)
    }

    @Test
    fun addPointToolDoesNothingOnAnExistingPointOrEmptySpace() {
        val (s, c) = setup()
        s.setPointTool(PointTool.AddPoint)
        c.down(100.0, 0.0)
        c.down(0.0, 0.0)
        assertEquals(4, contour(s).anchors.size)
        assertEquals(0, s.history.undoSize)
    }

    @Test
    fun deletePointToolRemovesTheTouchedPoint() {
        val (s, c) = setup()
        s.setPointTool(PointTool.DeletePoint)
        c.down(0.0, 99.0)
        assertEquals(listOf(1L, 3L, 4L), contour(s).anchors.map { it.id.value })
        assertTrue(s.undo())
        assertEquals(4, contour(s).anchors.size)
    }

    @Test
    fun eventsAreIgnoredInShapeMode() {
        val (s, c) = setup()
        s.exitPointMode()
        c.down(100.0, 0.0)
        c.move(150.0, 0.0)
        c.up()
        assertEquals(0, s.history.undoSize)
        assertEquals(emptySet(), s.state.selectedAnchors)
    }

    @Test
    fun cancelRestoresTheShape() {
        val (s, c) = setup()
        val before = s.document
        c.down(100.0, 0.0)
        c.move(140.0, 30.0)
        c.handle(PointerEvent(PointerType.Cancel, Vec2.ZERO))
        assertEquals(before, s.document)
        assertEquals(0, s.history.undoSize)
    }
}
