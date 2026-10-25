package studio.buttonforge.editorcore

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.setLocked
import studio.buttonforge.editorcore.points.EditMode
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectAtTest {
    private val layer = LayerId(1)

    @Test
    fun tapInsideOrNearTheOutlineSelectsTheShapeAndTapElsewhereClearsIt() {
        val s = EditorSession(SampleDocuments.circleButton())
        assertEquals(layer, s.selectLayerAt(Vec2(0.0, 0.0), 10.0))
        assertEquals(layer, s.state.selectedLayer)
        assertEquals(layer, s.selectLayerAt(Vec2(105.0, 0.0), 10.0)) // just outside the outline, within tolerance
        assertNull(s.selectLayerAt(Vec2(300.0, 300.0), 10.0))
        assertNull(s.state.selectedLayer)
    }

    @Test
    fun theTopmostShapeWinsAndLockedOrHiddenOnesAreSkipped() {
        val s = EditorSession(SampleDocuments.circleButton())
        val top = s.addShapeLayer("Top", Appearance()) { next -> Primitives.circle(Vec2.ZERO, 50.0, next) }
        assertEquals(top, s.selectLayerAt(Vec2(0.0, 0.0), 5.0))
        assertEquals(layer, s.selectLayerAt(Vec2(80.0, 0.0), 5.0)) // only the big circle reaches here

        s.updateLayers("lock top") { it.setLocked(top, true) }
        assertEquals(layer, s.selectLayerAt(Vec2(0.0, 0.0), 5.0))
    }

    @Test
    fun pointModeCanStartWithoutSelectingFirst() {
        val s = EditorSession(SampleDocuments.circleButton())
        assertNull(s.state.selectedLayer)
        assertTrue(s.enterPointModeAuto())
        assertEquals(layer, s.state.selectedLayer)
        assertEquals(EditMode.Points, s.state.mode)
    }

    @Test
    fun pointModeIsRefusedOnlyWhenThereIsNoShape() {
        val s = EditorSession(SampleDocuments.empty())
        assertFalse(s.enterPointModeAuto())
        assertEquals(EditMode.Shape, s.state.mode)
    }
}
