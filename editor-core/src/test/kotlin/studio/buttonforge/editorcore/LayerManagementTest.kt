package studio.buttonforge.editorcore

import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.LayerNode
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.groupLayers
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayerManagementTest {
    private fun threeLayers(): Pair<EditorSession, List<LayerId>> {
        val s = EditorSession(SampleDocuments.empty())
        val ids = listOf("A", "B", "C").map { name ->
            s.addShapeLayer(name, Appearance()) { next -> Primitives.circle(Vec2(100.0, 100.0), 40.0, next) }
        }
        return s to ids
    }

    private fun order(s: EditorSession) = s.document.layers.map { it.name }

    @Test
    fun reorderingMovesOneStepAndStopsAtTheEnds() {
        val (s, ids) = threeLayers()
        assertTrue(s.reorderLayer(ids[0], +1))
        assertEquals(listOf("B", "A", "C"), order(s))
        assertTrue(s.reorderLayer(ids[0], +5)) // clamped to the top
        assertEquals(listOf("B", "C", "A"), order(s))
        assertFalse(s.reorderLayer(ids[0], +1)) // already on top
        assertFalse(s.reorderLayer(LayerId(999), 1), "unknown layer")
        assertEquals(listOf("B", "C", "A"), order(s))
        assertTrue(s.undo())
        assertEquals(listOf("B", "A", "C"), order(s))
    }

    @Test
    fun deletingIsUndoableAndClearsTheSelection() {
        val (s, ids) = threeLayers()
        s.selectLayer(ids[1])
        assertTrue(s.deleteLayer(ids[1]))
        assertEquals(listOf("A", "C"), order(s))
        assertNull(s.state.selectedLayer)
        assertFalse(s.deleteLayer(ids[1]))
        assertTrue(s.undo())
        assertEquals(listOf("A", "B", "C"), order(s))
        assertEquals(emptyList(), DocumentInvariants.check(s.document))
    }

    @Test
    fun hidingAndLockingAreUndoableAndKeepTapSelectionAway() {
        val (s, ids) = threeLayers()
        assertTrue(s.setLayerVisible(ids[2], false))
        assertFalse(s.setLayerVisible(ids[2], false), "already hidden")
        assertFalse(s.document.findLayer(ids[2])!!.visible)
        // all three overlap at (100,100); the top one is hidden, so the tap reaches the next one
        assertEquals(ids[1], s.selectLayerAt(Vec2(100.0, 100.0), 5.0))

        assertTrue(s.setLayerLocked(ids[1], true))
        assertEquals(ids[0], s.selectLayerAt(Vec2(100.0, 100.0), 5.0))

        s.undo()
        s.undo()
        assertTrue(s.document.findLayer(ids[2])!!.visible)
        assertFalse(s.document.findLayer(ids[1])!!.locked)
    }

    @Test
    fun duplicatingCopiesTheShapeWithNewIdsAboveTheOriginal() {
        val (s, ids) = threeLayers()
        val original = s.document.findLayer(ids[0]) as ShapeLayer
        val copyId = s.duplicateLayer(ids[0])
        assertNotNull(copyId)

        assertEquals(listOf("A", "A نسخة", "B", "C"), order(s))
        assertEquals(copyId, s.state.selectedLayer)
        val copy = s.document.findLayer(copyId) as ShapeLayer
        assertEquals(original.shape.transform.translation + Vec2(20.0, 20.0), copy.shape.transform.translation)
        assertEquals(original.shape.geometry.contours[0].anchors.size, copy.shape.geometry.contours[0].anchors.size)
        val originalIds = original.shape.geometry.contours.flatMap { c -> c.anchors.map { it.id } }.toSet()
        assertTrue(copy.shape.geometry.contours.flatMap { c -> c.anchors.map { it.id } }.none { it in originalIds })
        assertEquals(emptyList(), DocumentInvariants.check(s.document))

        assertTrue(s.undo())
        assertEquals(listOf("A", "B", "C"), order(s))
        assertTrue(s.redo())
        assertEquals(4, s.document.layers.size)
    }

    @Test
    fun duplicatingAGroupCopiesEveryChildWithFreshIds() {
        val (s, ids) = threeLayers()
        val groupId = LayerId(s.document.counters.nextLayer)
        s.updateLayers("group") {
            it.groupLayers(listOf(ids[0], ids[1]), groupId).copy(counters = it.counters.copy(nextLayer = groupId.value + 1))
        }
        val copyId = s.duplicateLayer(groupId)
        assertNotNull(copyId)

        val copy = s.document.findLayer(copyId) as GroupLayer
        assertEquals(2, copy.children.size)
        fun allLayerIds(nodes: List<LayerNode>): List<LayerId> =
            nodes.flatMap { n -> listOf(n.id) + if (n is GroupLayer) allLayerIds(n.children) else emptyList() }
        val all = allLayerIds(s.document.layers)
        assertEquals(all.size, all.toSet().size)
        assertEquals(emptyList(), DocumentInvariants.check(s.document))
    }
}
