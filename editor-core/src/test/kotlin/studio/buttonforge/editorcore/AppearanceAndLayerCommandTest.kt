package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.addLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.removeLayer
import studio.buttonforge.document.shape.Shape
import studio.buttonforge.document.states.ButtonState
import studio.buttonforge.document.states.StateOverride
import studio.buttonforge.document.states.StateSet
import studio.buttonforge.geometry.path.CompoundPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppearanceAndLayerCommandTest {
    private val layer = LayerId(1)
    private fun appearanceOf(s: EditorSession) = (s.document.findLayer(layer) as ShapeLayer).shape.appearance

    @Test
    fun sliderChangesWithinTheWindowMergeAndLaterOnesDoNot() {
        val s = EditorSession(SampleDocuments.circleButton())
        val original = appearanceOf(s)
        for (i in 1..5) s.setAppearance(layer, Appearance(opacity = i / 10.0), "opacity", nowMillis = i * 100L)
        assertEquals(1, s.history.undoSize)
        s.setAppearance(layer, Appearance(opacity = 0.9), "opacity", nowMillis = 5_000L)
        assertEquals(2, s.history.undoSize)

        s.undo()
        assertEquals(Appearance(opacity = 0.5), appearanceOf(s))
        s.undo()
        assertEquals(original, appearanceOf(s))
    }

    @Test
    fun appearanceChangeNeverTouchesGeometry() {
        val s = EditorSession(SampleDocuments.circleButton())
        val geometry = (s.document.findLayer(layer) as ShapeLayer).shape.geometry
        s.setAppearance(layer, Appearance(opacity = 0.3))
        assertSame(geometry, (s.document.findLayer(layer) as ShapeLayer).shape.geometry)
    }

    @Test
    fun differentKeysOrNoKeyDoNotMerge() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.setAppearance(layer, Appearance(opacity = 0.1), "a", 0)
        s.setAppearance(layer, Appearance(opacity = 0.2), "b", 10)
        s.setAppearance(layer, Appearance(opacity = 0.3), null, 20)
        s.setAppearance(layer, Appearance(opacity = 0.4), null, 30)
        assertEquals(4, s.history.undoSize)
    }

    @Test
    fun layerOperationsUndoAndRedoIncludingStateOverrides() {
        val base = SampleDocuments.circleButton().copy(
            states = StateSet(mapOf(ButtonState.Pressed to mapOf(layer to StateOverride()))),
        )
        val s = EditorSession(base)
        val extra = ShapeLayer(LayerId(2), "Extra", shape = Shape(CompoundPath(emptyList())))

        s.updateLayers("Add layer") { it.addLayer(extra) }
        assertNotNull(s.document.findLayer(LayerId(2)))

        s.updateLayers("Remove layer") { it.removeLayer(layer) }
        assertNull(s.document.findLayer(layer))
        assertEquals(emptyMap(), s.document.states.overrides)

        s.undo()
        assertNotNull(s.document.findLayer(layer))
        assertEquals(base.states, s.document.states)
        s.undo()
        assertNull(s.document.findLayer(LayerId(2)))
        s.redo()
        assertNotNull(s.document.findLayer(LayerId(2)))
        // add, remove, undo, undo, redo: only "add" is back on the undo stack, "remove" waits on the redo stack
        assertEquals(1, s.history.undoSize)
        assertEquals(1, s.history.redoSize)
    }

    @Test
    fun anOperationThatChangesNothingRecordsNothing() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.updateLayers("nothing") { it }
        assertEquals(0, s.history.undoSize)
    }
}
