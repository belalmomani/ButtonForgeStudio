package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.geometry.PathDistance
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragCoalescingTest {
    private val layer = LayerId(1)

    private fun contour(session: EditorSession): Contour =
        (session.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.first()

    @Test
    fun tenThousandMoveEventsBecomeOneHistoryEntry() {
        val session = EditorSession(SampleDocuments.circleButton())
        val original = session.document
        assertTrue(session.beginAnchorDrag(layer, setOf(AnchorId(1), AnchorId(2))))
        repeat(10_000) { i -> session.updateAnchorDrag(Vec2(i * 0.01, -i * 0.02)) }
        assertEquals(0, session.history.undoSize, "nothing is recorded while dragging")
        assertTrue(session.commitAnchorDrag())
        assertEquals(1, session.history.undoSize)

        val last = Vec2(9_999 * 0.01, -9_999 * 0.02)
        val moved = session.document
        val a1 = contour(session).anchors.first { it.id == AnchorId(1) }
        assertEquals(100.0 + last.x, a1.position.x, 1e-9)
        assertEquals(0.0 + last.y, a1.position.y, 1e-9)

        assertTrue(session.undo())
        assertEquals(original, session.document)
        assertTrue(session.redo())
        assertEquals(moved, session.document)
    }

    @Test
    fun cancelRestoresTheDocumentAndRecordsNothing() {
        val session = EditorSession(SampleDocuments.circleButton())
        val original = session.document
        session.beginAnchorDrag(layer, setOf(AnchorId(3)))
        session.updateAnchorDrag(Vec2(50.0, 50.0))
        session.cancelAnchorDrag()
        assertEquals(original, session.document)
        assertEquals(0, session.history.undoSize)
        assertFalse(session.isDragging)
    }

    @Test
    fun aDragThatEndsWhereItStartedRecordsNothing() {
        val session = EditorSession(SampleDocuments.circleButton())
        session.beginAnchorDrag(layer, setOf(AnchorId(1)))
        session.updateAnchorDrag(Vec2(10.0, 10.0))
        session.updateAnchorDrag(Vec2(0.0, 0.0))
        assertFalse(session.commitAnchorDrag())
        assertEquals(0, session.history.undoSize)
    }

    @Test
    fun movingChangesOnlyTheTargetedPointsAndReallyChangesTheShape() {
        val session = EditorSession(SampleDocuments.circleButton())
        val before = contour(session)
        session.beginAnchorDrag(layer, setOf(AnchorId(1)))
        session.updateAnchorDrag(Vec2(30.0, 0.0))
        session.commitAnchorDrag()
        val after = contour(session)

        for ((b, a) in before.anchors.zip(after.anchors)) {
            assertEquals(b.id, a.id)
            assertEquals(b.handleIn, a.handleIn)
            assertEquals(b.handleOut, a.handleOut)
            if (b.id == AnchorId(1)) assertEquals(Vec2(130.0, 0.0), a.position) else assertEquals(b.position, a.position)
        }
        assertTrue(PathDistance.hausdorff(before, after) > 1.0)
    }

    @Test
    fun undoDuringADragCancelsItFirst() {
        val session = EditorSession(SampleDocuments.circleButton())
        session.updateLayers("noop-free change") { it.copy(name = it.name) } // no effect, no history
        session.beginAnchorDrag(layer, setOf(AnchorId(1)))
        session.updateAnchorDrag(Vec2(5.0, 5.0))
        assertFalse(session.undo())
        assertFalse(session.isDragging)
    }
}
