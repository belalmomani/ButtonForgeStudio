package studio.buttonforge.editorcore

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.editorcore.input.Viewport
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewportAndShapeTest {
    private fun empty() = EditorSession(Document("New", CanvasSize(512.0, 512.0)))

    @Test
    fun screenAndWorldAreInverse() {
        val v = Viewport(2.5, 30.0, -12.0)
        val p = Vec2(17.25, 99.5)
        val back = v.toWorld(v.toScreen(p))
        assertEquals(p.x, back.x, 1e-12)
        assertEquals(p.y, back.y, 1e-12)
    }

    @Test
    fun zoomKeepsThePointUnderTheFingerFixedAndIsClamped() {
        val v = Viewport(1.0, 10.0, 20.0)
        val anchor = Vec2(200.0, 150.0)
        val worldBefore = v.toWorld(anchor)
        val zoomed = v.zoomedAbout(anchor.x, anchor.y, 3.0)
        val worldAfter = zoomed.toWorld(anchor)
        assertEquals(worldBefore.x, worldAfter.x, 1e-9)
        assertEquals(worldBefore.y, worldAfter.y, 1e-9)
        assertEquals(3.0, zoomed.scale, 1e-12)
        assertEquals(Viewport.MAX_SCALE, v.zoomedAbout(0.0, 0.0, 1e9).scale)
        assertEquals(Viewport.MIN_SCALE, v.zoomedAbout(0.0, 0.0, 1e-9).scale)
        assertEquals(v, v.zoomedAbout(0.0, 0.0, -1.0))
    }

    @Test
    fun fitCentresTheCanvas() {
        val v = Viewport.fit(512.0, 512.0, 1000.0, 2000.0, margin = 1.0)
        assertEquals(1000.0 / 512.0, v.scale, 1e-12)
        val topLeft = v.toScreen(Vec2.ZERO)
        val bottomRight = v.toScreen(Vec2(512.0, 512.0))
        assertEquals(0.0, topLeft.x, 1e-9)
        assertEquals(1000.0, bottomRight.x, 1e-9)
        assertEquals(2000.0 - bottomRight.y, topLeft.y, 1e-9)
    }

    @Test
    fun invalidScaleIsRejected() {
        assertFailsWith<IllegalArgumentException> { Viewport(0.0) }
    }

    @Test
    fun addingShapesIsOneUndoStepAndKeepsIdsUnique() {
        val s = empty()
        val a = s.addShapeLayer("Circle", Appearance()) { next -> Primitives.circle(Vec2(100.0, 100.0), 50.0, next) }
        val b = s.addShapeLayer("Star", Appearance()) { next -> Primitives.star(Vec2(300.0, 300.0), 80.0, 30.0, 5, next) }
        assertEquals(2, s.history.undoSize)
        assertEquals(b, s.state.selectedLayer)
        assertEquals(emptyList(), DocumentInvariants.check(s.document))
        assertEquals(4 + 10, s.document.counters.nextAnchor - 1)

        val counters = s.document.counters
        assertTrue(s.undo())
        assertNull(s.document.findLayer(b))
        assertNotNull(s.document.findLayer(a))
        assertEquals(null, s.state.selectedLayer, "selection of a removed layer is cleared")
        assertEquals(counters, s.document.counters, "counters never go back")
        assertTrue(s.redo())
        assertNotNull(s.document.findLayer(b))
        assertEquals(10, ((s.document.findLayer(b) as ShapeLayer).shape.geometry.contours.single().anchors.size))
    }
}
