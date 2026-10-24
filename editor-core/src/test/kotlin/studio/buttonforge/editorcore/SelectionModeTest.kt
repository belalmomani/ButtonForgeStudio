package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.geometry.path.AnchorId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionModeTest {
    private val layer = LayerId(1)
    private fun session() = EditorSession(SampleDocuments.circleButton())

    @Test
    fun startsWithNothingSelectedInShapeMode() {
        val s = session()
        assertNull(s.state.selectedLayer)
        assertEquals(EditMode.Shape, s.state.mode)
        assertFalse(s.enterPointMode())
    }

    @Test
    fun tapOnEmptySpaceDeselectsTheShapeInShapeModeButOnlyThePointsInPointsMode() {
        val s = session()
        s.selectLayer(layer)
        assertTrue(s.enterPointMode())
        s.selectAnchors(setOf(AnchorId(1), AnchorId(2)), additive = false)
        s.tapEmpty()
        assertEquals(emptySet(), s.state.selectedAnchors)
        assertEquals(layer, s.state.selectedLayer)
        assertEquals(EditMode.Points, s.state.mode)

        s.exitPointMode()
        assertEquals(EditMode.Shape, s.state.mode)
        assertEquals(layer, s.state.selectedLayer)
        s.tapEmpty()
        assertNull(s.state.selectedLayer)
    }

    @Test
    fun additiveSelectionAddsAndPlainSelectionReplaces() {
        val s = session()
        s.selectLayer(layer)
        s.enterPointMode()
        s.selectAnchors(setOf(AnchorId(1)), additive = false)
        s.selectAnchors(setOf(AnchorId(2)), additive = true)
        assertEquals(setOf(AnchorId(1), AnchorId(2)), s.state.selectedAnchors)
        s.selectAnchors(setOf(AnchorId(3)), additive = false)
        assertEquals(setOf(AnchorId(3)), s.state.selectedAnchors)
    }

    @Test
    fun pointsCannotBeSelectedInShapeMode() {
        val s = session()
        s.selectLayer(layer)
        assertFailsWith<IllegalStateException> { s.selectAnchors(setOf(AnchorId(1)), false) }
    }

    @Test
    fun deletedPointsLeaveTheSelection() {
        val s = session()
        s.selectLayer(layer)
        s.enterPointMode()
        s.selectAnchors(setOf(AnchorId(1), AnchorId(2)), false)
        s.deleteAnchor(layer, AnchorId(2))
        assertEquals(setOf(AnchorId(1)), s.state.selectedAnchors)
    }

    @Test
    fun selectingAMissingLayerClearsEverything() {
        val s = session()
        s.selectLayer(layer)
        s.selectLayer(LayerId(99))
        assertNull(s.state.selectedLayer)
    }
}
