package studio.buttonforge.document

import studio.buttonforge.document.layers.GroupLayer
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.addLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.groupLayers
import studio.buttonforge.document.layers.locateLayer
import studio.buttonforge.document.layers.moveLayer
import studio.buttonforge.document.layers.removeLayer
import studio.buttonforge.document.layers.setLocked
import studio.buttonforge.document.layers.setVisible
import studio.buttonforge.document.layers.ungroup
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.states.ButtonState
import studio.buttonforge.document.states.StateOverride
import studio.buttonforge.document.states.StateSet
import studio.buttonforge.geometry.path.CompoundPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayerOperationsTest {
    private fun layer(id: Long) = ShapeLayer(LayerId(id), "L$id", shape = Shape(CompoundPath(emptyList())))
    private fun ids(doc: Document) = doc.layers.map { it.id.value }

    private fun three(): Document =
        SampleDocuments.empty().addLayer(layer(1)).addLayer(layer(2)).addLayer(layer(3))

    @Test
    fun addAppendsAndInsertsAtIndex() {
        val doc = three().addLayer(layer(4), index = 1)
        assertEquals(listOf(1L, 4L, 2L, 3L), ids(doc))
    }

    @Test
    fun duplicateIdIsRejected() {
        assertFailsWith<IllegalArgumentException> { three().addLayer(layer(2)) }
    }

    @Test
    fun addIntoGroupAndFindNested() {
        val doc = SampleDocuments.empty()
            .addLayer(GroupLayer(LayerId(10), "G"))
            .addLayer(layer(1), parent = LayerId(10))
        assertEquals(1L, (doc.findLayer(LayerId(1)) as ShapeLayer).id.value)
        assertEquals(1, (doc.findLayer(LayerId(10)) as GroupLayer).children.size)
        assertFailsWith<IllegalArgumentException> { doc.addLayer(layer(2), parent = LayerId(1)) } // not a group
        assertFailsWith<IllegalArgumentException> { doc.addLayer(layer(2), parent = LayerId(99)) } // missing
    }

    @Test
    fun removeDeletesSubtreeAndItsStateOverrides() {
        val base = SampleDocuments.empty()
            .addLayer(GroupLayer(LayerId(10), "G"))
            .addLayer(layer(1), parent = LayerId(10))
            .addLayer(layer(2))
        val withStates = base.copy(
            states = StateSet(
                mapOf(ButtonState.Pressed to mapOf(LayerId(1) to StateOverride(), LayerId(2) to StateOverride())),
            ),
        )
        val doc = withStates.removeLayer(LayerId(10))
        assertNull(doc.findLayer(LayerId(1)))
        assertEquals(setOf(LayerId(2)), doc.states.overrides.getValue(ButtonState.Pressed).keys)
    }

    @Test
    fun moveReordersAndReparents() {
        val doc = three().moveLayer(LayerId(3), newParent = null, index = 0)
        assertEquals(listOf(3L, 1L, 2L), ids(doc))
        val grouped = three().addLayer(GroupLayer(LayerId(10), "G")).moveLayer(LayerId(1), LayerId(10))
        assertEquals(listOf(2L, 3L, 10L), ids(grouped))
        assertEquals(1, (grouped.findLayer(LayerId(10)) as GroupLayer).children.size)
    }

    @Test
    fun moveIntoOwnDescendantIsRejected() {
        val doc = SampleDocuments.empty()
            .addLayer(GroupLayer(LayerId(10), "outer"))
            .addLayer(GroupLayer(LayerId(11), "inner"), parent = LayerId(10))
        assertFailsWith<IllegalArgumentException> { doc.moveLayer(LayerId(10), LayerId(11)) }
        assertFailsWith<IllegalArgumentException> { doc.moveLayer(LayerId(10), LayerId(10)) }
    }

    @Test
    fun groupThenUngroupRestoresOrder() {
        val doc = three().addLayer(layer(4))
        val grouped = doc.groupLayers(listOf(LayerId(3), LayerId(2)), LayerId(20))
        assertEquals(listOf(1L, 20L, 4L), ids(grouped))
        assertEquals(listOf(2L, 3L), (grouped.findLayer(LayerId(20)) as GroupLayer).children.map { it.id.value })
        assertEquals(ids(doc), ids(grouped.ungroup(LayerId(20))))
    }

    @Test
    fun groupRequiresSiblings() {
        val doc = SampleDocuments.empty()
            .addLayer(GroupLayer(LayerId(10), "G"))
            .addLayer(layer(1), parent = LayerId(10))
            .addLayer(layer(2))
        assertFailsWith<IllegalArgumentException> { doc.groupLayers(listOf(LayerId(1), LayerId(2)), LayerId(30)) }
    }

    @Test
    fun visibilityAndLockFlags() {
        val doc = three().setVisible(LayerId(2), false).setLocked(LayerId(3), true)
        assertEquals(false, doc.findLayer(LayerId(2))!!.visible)
        assertTrue(doc.findLayer(LayerId(3))!!.locked)
        assertTrue(doc.findLayer(LayerId(1))!!.visible)
    }

    @Test
    fun locateReportsParentIndexAndSiblingCount() {
        val doc = SampleDocuments.empty()
            .addLayer(layer(1))
            .addLayer(GroupLayer(LayerId(10), "G"))
            .addLayer(layer(2), parent = LayerId(10))
            .addLayer(layer(3), parent = LayerId(10))
        assertEquals(studio.buttonforge.document.layers.LayerLocation(null, 1, 2), doc.locateLayer(LayerId(10)))
        assertEquals(studio.buttonforge.document.layers.LayerLocation(LayerId(10), 1, 2), doc.locateLayer(LayerId(3)))
        assertNull(doc.locateLayer(LayerId(99)))
    }
}
