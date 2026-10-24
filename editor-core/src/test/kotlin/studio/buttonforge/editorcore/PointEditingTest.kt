package studio.buttonforge.editorcore

import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.geometry.PathDistance
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.path.Contour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointEditingTest {
    private val layer = LayerId(1)

    private fun contour(session: EditorSession): Contour =
        (session.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.first()

    @Test
    fun addPointsToCountReachesExactCountKeepsShapeAndIsOneUndoStep() {
        for (target in listOf(25, 50, 100, 250, 500)) {
            val session = EditorSession(SampleDocuments.circleButton())
            val original = session.document
            val before = contour(session)
            assertTrue(session.addPointsToCount(layer, 0, target))
            val after = contour(session)

            assertEquals(target, after.anchors.size)
            assertEquals(target, after.anchors.map { it.id }.toSet().size)
            assertEquals(emptyList(), DocumentInvariants.check(session.document))
            assertTrue(PathDistance.hausdorff(before, after) <= 1e-6 * PathDistance.diagonal(before), "target=$target")
            assertEquals(1, session.history.undoSize)

            val counterAfter = session.document.counters.nextAnchor
            assertTrue(session.undo())
            assertEquals(original.layers, session.document.layers)
            assertEquals(counterAfter, session.document.counters.nextAnchor, "ids are never handed out again")
            assertTrue(session.redo())
            assertEquals(target, contour(session).anchors.size)
        }
    }

    @Test
    fun targetBelowCurrentCountIsRejected() {
        val session = EditorSession(SampleDocuments.circleButton())
        assertFailsWith<IllegalArgumentException> { session.addPointsToCount(layer, 0, 3) }
    }

    @Test
    fun insertAnchorKeepsShapeAndEnds() {
        val session = EditorSession(SampleDocuments.circleButton())
        val before = contour(session)
        val id = session.insertAnchorAt(layer, 0, 1, 0.4)
        assertNotNull(id)
        assertEquals(5, contour(session).anchors.size)
        assertTrue(PathDistance.hausdorff(before, contour(session)) <= 1e-6 * PathDistance.diagonal(before))
        assertNull(session.insertAnchorAt(layer, 0, 1, 0.0))
        assertNull(session.insertAnchorAt(layer, 0, 1, 1.0))
        assertNull(session.insertAnchorAt(layer, 0, 99, 0.5))
    }

    @Test
    fun deleteAnchorChangesTheShapeAndUndoRestoresIt() {
        val session = EditorSession(SampleDocuments.circleButton())
        val original = session.document
        assertTrue(session.deleteAnchor(layer, AnchorId(2)))
        assertEquals(3, contour(session).anchors.size)
        assertTrue(session.undo())
        assertEquals(original.layers, session.document.layers)
        assertFalse(session.deleteAnchor(layer, AnchorId(999)))
    }

    @Test
    fun cannotDeleteDownToFewerThanTwoAnchors() {
        val session = EditorSession(SampleDocuments.circleButton())
        assertTrue(session.deleteAnchor(layer, AnchorId(1)))
        assertTrue(session.deleteAnchor(layer, AnchorId(2)))
        assertFalse(session.deleteAnchor(layer, AnchorId(3)))
        assertEquals(2, contour(session).anchors.size)
    }
}
