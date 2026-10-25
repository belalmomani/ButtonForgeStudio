package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.Anchor
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.CornerType
import studio.buttonforge.geometry.path.HandleSide
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleEditingTest {
    private val layer = LayerId(1)

    private fun anchor(s: EditorSession, id: Long): Anchor =
        (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.flatMap { it.anchors }.first { it.id == AnchorId(id) }

    @Test
    fun draggingAHandleIsOneUndoStepAndTheOtherHandleFollowsTheCornerType() {
        val s = EditorSession(SampleDocuments.circleButton())
        val original = s.document
        val before = anchor(s, 1)
        assertEquals(CornerType.Smooth, before.corner)

        assertTrue(s.beginHandleDrag(layer, AnchorId(1), HandleSide.Out))
        repeat(1000) { i -> s.updateHandleDrag(Vec2(i * 0.05, i * 0.02)) }
        assertEquals(0, s.history.undoSize, "nothing is recorded while dragging")
        assertTrue(s.commitHandleDrag())
        assertEquals(1, s.history.undoSize)

        val after = anchor(s, 1)
        assertEquals(before.position, after.position)
        assertEquals(before.handleOut + Vec2(999 * 0.05, 999 * 0.02), after.handleOut)
        assertEquals(before.handleIn.length(), after.handleIn.length(), 1e-9)
        assertTrue(abs(after.handleIn.x * after.handleOut.y - after.handleIn.y * after.handleOut.x) < 1e-6)

        assertTrue(s.undo())
        assertEquals(original, s.document)
        assertTrue(s.redo())
        assertEquals(after, anchor(s, 1))
    }

    @Test
    fun cancelledOrUnmovedHandleDragsRecordNothing() {
        val s = EditorSession(SampleDocuments.circleButton())
        val original = s.document
        s.beginHandleDrag(layer, AnchorId(2), HandleSide.In)
        s.updateHandleDrag(Vec2(20.0, 20.0))
        s.cancelAnchorDrag()
        assertEquals(original, s.document)
        assertFalse(s.isDragging)

        s.beginHandleDrag(layer, AnchorId(2), HandleSide.In)
        s.updateHandleDrag(Vec2(5.0, 5.0))
        s.updateHandleDrag(Vec2.ZERO)
        assertFalse(s.commitHandleDrag())
        assertEquals(0, s.history.undoSize)
    }

    @Test
    fun cornerTypeChangesAreOneUndoStepAcrossSeveralAnchors() {
        val s = EditorSession(SampleDocuments.empty())
        val id = s.addShapeLayer("Star", studio.buttonforge.document.appearance.Appearance()) { next ->
            Primitives.star(Vec2.ZERO, 100.0, 40.0, 5, next)
        }
        val ids = (s.document.findLayer(id) as ShapeLayer).shape.geometry.contours.single().anchors.take(4).map { it.id }.toSet()
        val original = s.document

        assertTrue(s.setCornerType(id, ids, CornerType.Smooth))
        val smooth = (s.document.findLayer(id) as ShapeLayer).shape.geometry.contours.single().anchors
        assertTrue(smooth.take(4).all { it.corner == CornerType.Smooth && it.handleOut.length() > 0.0 })
        assertTrue(smooth.drop(4).all { it.corner == CornerType.Sharp })
        assertEquals(2, s.history.undoSize) // adding the shape, then the corner change

        assertTrue(s.undo())
        assertEquals(original.layers, s.document.layers)
        assertFalse(s.setCornerType(id, ids, CornerType.Sharp), "already sharp: nothing to do")
    }

    @Test
    fun touchingAHandleOfASelectedPointMovesTheHandleNotThePoint() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.selectLayer(layer)
        s.enterPointMode()
        s.selectAnchors(setOf(AnchorId(1)), false)
        val viewport = object : ViewportTransform {
            override val scale = 1.0
            override fun toWorld(screen: Vec2) = screen
        }
        val c = PointModeController(s, viewport, TouchSettings(24.0))
        val before = anchor(s, 1)
        val tip = before.position + before.handleOut // (100, 55.2)

        c.handle(PointerEvent(PointerType.Down, tip + Vec2(2.0, 1.0)))
        c.handle(PointerEvent(PointerType.Move, tip + Vec2(2.0, 1.0) + Vec2(30.0, 0.0)))
        c.handle(PointerEvent(PointerType.Up, Vec2.ZERO))

        val after = anchor(s, 1)
        assertEquals(before.position, after.position)
        assertEquals(before.handleOut.x + 30.0, after.handleOut.x, 1e-9)
        assertEquals(1, s.history.undoSize)
    }

    @Test
    fun handlesOfUnselectedPointsCannotBeGrabbed() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.selectLayer(layer)
        s.enterPointMode()
        val viewport = object : ViewportTransform {
            override val scale = 1.0
            override fun toWorld(screen: Vec2) = screen
        }
        val c = PointModeController(s, viewport, TouchSettings(24.0))
        val a = anchor(s, 1)
        c.handle(PointerEvent(PointerType.Down, a.position + a.handleOut))
        c.handle(PointerEvent(PointerType.Move, a.position + a.handleOut + Vec2(30.0, 0.0)))
        c.handle(PointerEvent(PointerType.Up, Vec2.ZERO))
        assertEquals(a, anchor(s, 1))
    }
}
