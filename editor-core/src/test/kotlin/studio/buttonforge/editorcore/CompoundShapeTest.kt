package studio.buttonforge.editorcore

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.geometry.hittest.HitTester
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.FillRule
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompoundShapeTest {
    private fun twoCircles(): Triple<EditorSession, LayerId, LayerId> {
        val s = EditorSession(Document("d", CanvasSize(1000.0, 1000.0)))
        val big = s.addShapeLayer("Big", Appearance()) { next -> Primitives.circle(Vec2.ZERO, 100.0, next) }
        val small = s.addShapeLayer("Small", Appearance()) { next -> Primitives.circle(Vec2.ZERO, 40.0, next) }
        return Triple(s, big, small)
    }

    private fun shape(s: EditorSession, id: LayerId) = (s.document.findLayer(id) as ShapeLayer).shape

    @Test
    fun combiningMakesAHoleAndIsOneUndoStep() {
        val (s, big, small) = twoCircles()
        val before = s.document
        assertTrue(s.combineWithBelow(small))

        assertEquals(1, s.document.layers.size)
        assertEquals(big, s.state.selectedLayer)
        val g = shape(s, big).geometry
        assertEquals(2, g.contours.size)
        assertEquals(FillRule.EvenOdd, g.fillRule)
        assertFalse(HitTester.contains(g, Vec2(0.0, 0.0)), "the middle is a hole")
        assertTrue(HitTester.contains(g, Vec2(70.0, 0.0)), "the ring is filled")
        assertEquals(emptyList(), DocumentInvariants.check(s.document))

        assertTrue(s.undo())
        assertEquals(before.layers, s.document.layers)
        assertEquals(2, s.document.layers.size)
    }

    @Test
    fun theUpperShapeIsMovedIntoTheLowerShapesCoordinates() {
        val (s, big, small) = twoCircles()
        s.updateLayers("move") {
            it.updateShape(big) { sh -> sh.copy(transform = Transform(translation = Vec2(300.0, 0.0))) }
                .updateShape(small) { sh -> sh.copy(transform = Transform(translation = Vec2(320.0, 10.0))) }
        }
        val smallWorld = shape(s, small).geometry.contours.single().anchors.map { shape(s, small).transform.toWorld(it.position) }
        assertTrue(s.combineWithBelow(small))
        val g = shape(s, big)
        val merged = g.geometry.contours[1].anchors.map { g.transform.toWorld(it.position) }
        for ((a, b) in smallWorld.zip(merged)) assertTrue(a.distanceTo(b) < 1e-9, "$a vs $b")
    }

    @Test
    fun combiningNeedsAShapeDirectlyBelow() {
        val (s, big, _) = twoCircles()
        assertFalse(s.combineWithBelow(big), "nothing below the bottom layer")
        assertFalse(s.combineWithBelow(LayerId(999)))
        assertEquals(2, s.document.layers.size)
        assertEquals(2, s.history.undoSize, "only the two shape additions are in the history")
    }

    @Test
    fun splittingGivesEachContourItsOwnLayerAndKeepsIdsUnique() {
        val (s, big, small) = twoCircles()
        s.combineWithBelow(small)
        assertFalse(s.splitContours(LayerId(999)))

        assertTrue(s.splitContours(big))
        assertEquals(2, s.document.layers.size)
        assertEquals(1, shape(s, big).geometry.contours.size)
        val second = s.document.layers[1] as ShapeLayer
        assertEquals(1, second.shape.geometry.contours.size)
        assertEquals(emptyList(), DocumentInvariants.check(s.document))

        assertTrue(s.undo())
        assertEquals(1, s.document.layers.size)
        assertEquals(2, shape(s, big).geometry.contours.size)
        assertFalse(s.splitContours(second.id), "already a single contour")
        assertNull(s.document.findLayer(second.id))
    }
}
