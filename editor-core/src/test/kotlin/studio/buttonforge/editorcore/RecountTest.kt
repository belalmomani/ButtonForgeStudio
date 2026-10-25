package studio.buttonforge.editorcore

import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.geometry.path.Contour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecountTest {
    private val layer = LayerId(1)

    private fun contour(s: EditorSession): Contour =
        (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.first()

    @Test
    fun aPreviewChangesNothing() {
        val s = EditorSession(SampleDocuments.circleButton())
        val before = s.document
        val p = s.previewRecount(layer, 0, 100)
        assertEquals(before, s.document)
        assertEquals(0, s.history.undoSize)
        assertEquals(100, p.after.anchors.size)
        assertTrue(p.maxError < 0.2, "error=${p.maxError}")
    }

    @Test
    fun applyingIsOneUndoStepWithFreshIds() {
        val s = EditorSession(SampleDocuments.circleButton())
        val original = s.document
        val oldIds = contour(s).anchors.map { it.id }.toSet()
        s.applyReplacement(s.previewRecount(layer, 0, 100))

        assertEquals(100, contour(s).anchors.size)
        assertTrue(contour(s).anchors.none { it.id in oldIds })
        assertEquals(emptyList(), DocumentInvariants.check(s.document))
        assertEquals(1, s.history.undoSize)

        val counters = s.document.counters
        assertTrue(s.undo())
        assertEquals(original.layers, s.document.layers)
        assertEquals(counters, s.document.counters, "ids are never handed out again")
        assertTrue(s.redo())
        assertEquals(100, contour(s).anchors.size)
    }

    @Test
    fun fewerPointsReportAnHonestLargeError() {
        val s = EditorSession(SampleDocuments.circleButton())
        assertTrue(s.previewRecount(layer, 0, 3).maxError > 3.0)
    }

    @Test
    fun aStalePreviewIsRejected() {
        val s = EditorSession(SampleDocuments.circleButton())
        val preview = s.previewRecount(layer, 0, 50)
        s.insertAnchorAt(layer, 0, 1, 0.5)
        assertFailsWith<IllegalStateException> { s.applyReplacement(preview) }
    }

    @Test
    fun targetsBelowTheMinimumAreRejected() {
        val s = EditorSession(SampleDocuments.circleButton())
        assertFailsWith<IllegalArgumentException> { s.previewRecount(layer, 0, 2) }
        assertFailsWith<IllegalArgumentException> { s.previewRecount(layer, 5, 10) }
    }

    @Test
    fun simplifyingAPointHeavyCircleKeepsItWithinTheTolerance() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.addPointsToCount(layer, 0, 500)
        val preview = s.previewSimplify(layer, 0, tolerance = 0.5)
        assertTrue(preview.after.anchors.size < 60, "anchors=${preview.after.anchors.size}")
        assertTrue(preview.maxError <= 0.6, "error=${preview.maxError}")

        s.applyReplacement(preview)
        assertEquals(preview.after.anchors.size, contour(s).anchors.size)
        assertTrue(s.undo())
        assertEquals(500, contour(s).anchors.size)
    }
}
