package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.AnchorId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionToolsTest {
    private val layer = LayerId(1)

    private fun pointSession(): Pair<EditorSession, PointModeController> {
        val s = EditorSession(SampleDocuments.circleButton())
        s.selectLayer(layer)
        s.enterPointMode()
        val view = object : ViewportTransform {
            override val scale = 1.0
            override fun toWorld(screen: Vec2) = screen
        }
        return s to PointModeController(s, view, TouchSettings(24.0))
    }

    private fun PointModeController.drag(from: Vec2, to: Vec2) {
        handle(PointerEvent(PointerType.Down, from))
        handle(PointerEvent(PointerType.Move, to))
        handle(PointerEvent(PointerType.Up, Vec2.ZERO))
    }

    private fun ids(vararg n: Long) = n.map { AnchorId(it) }.toSet()

    @Test
    fun draggingFromEmptySpaceSelectsThePointsInsideTheRectangle() {
        val (s, c) = pointSession()
        // anchors: 1 (100,0)  2 (0,100)  3 (-100,0)  4 (0,-100)
        c.drag(Vec2(-150.0, -150.0), Vec2(150.0, 50.0))
        assertEquals(ids(1, 3, 4), s.state.selectedAnchors)
        assertNull(c.marquee, "the rectangle is gone once the finger lifts")
        assertEquals(0, s.history.undoSize)
    }

    @Test
    fun theRectangleCanBeDraggedInAnyDirectionAndCanAddToTheSelection() {
        val (s, c) = pointSession()
        c.drag(Vec2(150.0, 50.0), Vec2(-150.0, -150.0)) // right-to-left, bottom-to-top
        assertEquals(ids(1, 3, 4), s.state.selectedAnchors)

        c.additiveSelection = true
        c.drag(Vec2(-30.0, 60.0), Vec2(30.0, 140.0)) // around anchor 2 only
        assertEquals(ids(1, 2, 3, 4), s.state.selectedAnchors)
    }

    @Test
    fun theRectangleIsLiveWhileDraggingAndAShortDragIsJustATap() {
        val (s, c) = pointSession()
        s.selectAnchors(ids(1), false)
        c.handle(PointerEvent(PointerType.Down, Vec2(0.0, 0.0)))
        assertNull(c.marquee)
        c.handle(PointerEvent(PointerType.Move, Vec2(2.0, 2.0)))
        assertNull(c.marquee, "under the 6px threshold")
        c.handle(PointerEvent(PointerType.Move, Vec2(80.0, 80.0)))
        assertNotNull(c.marquee)
        c.handle(PointerEvent(PointerType.Cancel, Vec2.ZERO))
        assertNull(c.marquee)

        c.drag(Vec2(0.0, 0.0), Vec2(1.0, 1.0))
        assertEquals(emptySet(), s.state.selectedAnchors, "a tap on empty space clears the points")
    }

    @Test
    fun theRectangleUsesWhereThePointsAreShownNotTheirLocalCoordinates() {
        val (s, c) = pointSession()
        s.updateLayers("move") { it.updateShape(layer) { sh -> sh.copy(transform = Transform(translation = Vec2(500.0, 0.0))) } }
        c.drag(Vec2(550.0, -50.0), Vec2(650.0, 50.0)) // around the anchor shown at (600, 0)
        assertEquals(ids(1), s.state.selectedAnchors)
    }

    @Test
    fun selectAllAndInvert() {
        val (s, _) = pointSession()
        s.selectAllAnchors()
        assertEquals(ids(1, 2, 3, 4), s.state.selectedAnchors)
        s.selectAnchors(ids(2), false)
        s.invertAnchorSelection()
        assertEquals(ids(1, 3, 4), s.state.selectedAnchors)
    }

    @Test
    fun nudgesInQuickSuccessionBecomeOneUndoStep() {
        val s = EditorSession(SampleDocuments.circleButton())
        val before = (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.single().anchors
        assertTrue(s.nudgeAnchors(layer, ids(1, 2), Vec2(1.0, 0.0), 1000))
        assertTrue(s.nudgeAnchors(layer, ids(1, 2), Vec2(1.0, 0.0), 1200))
        assertTrue(s.nudgeAnchors(layer, ids(1, 2), Vec2(0.0, 2.0), 1400))
        assertEquals(1, s.history.undoSize)

        val after = (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.single().anchors
        assertEquals(before[0].position + Vec2(2.0, 2.0), after[0].position)
        assertEquals(before[2].position, after[2].position)

        assertTrue(s.nudgeAnchors(layer, ids(1, 2), Vec2(1.0, 0.0), 5000)) // much later: a new step
        assertEquals(2, s.history.undoSize)
        assertTrue(s.undo())
        assertTrue(s.undo())
        assertEquals(before, (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.single().anchors)
    }

    @Test
    fun nudgesOfDifferentPointsOrNothingAreNotMerged() {
        val s = EditorSession(SampleDocuments.circleButton())
        assertTrue(s.nudgeAnchors(layer, ids(1), Vec2(1.0, 0.0), 0))
        assertTrue(s.nudgeAnchors(layer, ids(2), Vec2(1.0, 0.0), 100))
        assertEquals(2, s.history.undoSize)
        assertFalse(s.nudgeAnchors(layer, ids(1), Vec2.ZERO, 200))
        assertFalse(s.nudgeAnchors(layer, ids(99), Vec2(1.0, 0.0), 200))
    }
}
