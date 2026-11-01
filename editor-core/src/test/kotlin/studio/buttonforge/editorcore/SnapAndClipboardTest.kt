package studio.buttonforge.editorcore

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.document.shape.Transform
import studio.buttonforge.editorcore.clipboard.Clipboard
import studio.buttonforge.editorcore.draw.DrawTool
import studio.buttonforge.editorcore.draw.PenController
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.ViewportTransform
import studio.buttonforge.editorcore.snapping.SnapContext
import studio.buttonforge.editorcore.snapping.SnapSettings
import studio.buttonforge.editorcore.snapping.Snapper
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.AnchorId
import studio.buttonforge.geometry.primitives.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapAndClipboardTest {
    private val layer = LayerId(1)

    private fun anchor(s: EditorSession, id: Long) =
        (s.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.flatMap { it.anchors }.first { it.id == AnchorId(id) }

    @Test
    fun theGridRoundsInBothDirectionsAndAPointWinsWhenNear() {
        assertEquals(Vec2(110.0, 0.0), Snapper.snapToGrid(Vec2(107.0, 3.0), 10.0))
        assertEquals(Vec2(-20.0, 10.0), Snapper.snapToGrid(Vec2(-18.0, 6.0), 10.0))
        val points = listOf(Vec2(5.0, 5.0), Vec2(50.0, 50.0))
        assertEquals(Vec2(5.0, 5.0), Snapper.snap(Vec2(7.0, 6.0), 10.0, points, 4.0), "a near point beats the grid")
        assertEquals(Vec2(10.0, 10.0), Snapper.snap(Vec2(11.0, 14.0), 10.0, points, 4.0), "far from points: the grid")
        assertEquals(Vec2(11.0, 14.0), Snapper.snap(Vec2(11.0, 14.0), null, points, 0.0), "nothing switched on")
        assertFailsWith<IllegalArgumentException> { SnapSettings(gridSize = 0.0) }
    }

    @Test
    fun draggingSnapsTheFirstPointToTheGridAndTheOthersFollowRigidly() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.beginAnchorDrag(layer, setOf(AnchorId(1), AnchorId(2)))
        s.updateAnchorDrag(Vec2(7.0, 3.0), SnapContext(gridSize = 10.0, anchorTolerance = 0.0))
        s.commitAnchorDrag()
        assertEquals(Vec2(110.0, 0.0), anchor(s, 1).position)
        assertEquals(Vec2(10.0, 100.0), anchor(s, 2).position, "moved by the same snapped step")
    }

    @Test
    fun draggingSnapsOntoAnotherPointAndNeverOntoItself() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.beginAnchorDrag(layer, setOf(AnchorId(1)))
        // anchor 1 starts at (100,0); anchor 2 is at (0,100); this puts it 5.4 away from anchor 2
        s.updateAnchorDrag(Vec2(-95.0, 98.0), SnapContext(gridSize = null, anchorTolerance = 8.0))
        s.commitAnchorDrag()
        assertEquals(Vec2(0.0, 100.0), anchor(s, 1).position)

        val t = EditorSession(SampleDocuments.circleButton())
        t.beginAnchorDrag(layer, setOf(AnchorId(1)))
        t.updateAnchorDrag(Vec2(3.0, 2.0), SnapContext(gridSize = null, anchorTolerance = 8.0)) // near its own start only
        t.commitAnchorDrag()
        assertEquals(Vec2(103.0, 2.0), anchor(t, 1).position)
    }

    @Test
    fun snappingWorksInWorldCoordinatesForAMovedShape() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.updateLayers("move") { it.updateShape(layer) { sh -> sh.copy(transform = Transform(translation = Vec2(3.0, 0.0))) } }
        s.beginAnchorDrag(layer, setOf(AnchorId(1)))
        s.updateAnchorDrag(Vec2(4.0, 0.0), SnapContext(gridSize = 10.0, anchorTolerance = 0.0)) // world (103+4) -> 110
        s.commitAnchorDrag()
        assertEquals(107.0, anchor(s, 1).position.x, 1e-9, "local = world 110 minus the shape's offset 3")
    }

    @Test
    fun withoutSnapContextADragIsUnchanged() {
        val s = EditorSession(SampleDocuments.circleButton())
        s.beginAnchorDrag(layer, setOf(AnchorId(1)))
        s.updateAnchorDrag(Vec2(7.0, 3.0))
        s.commitAnchorDrag()
        assertEquals(Vec2(107.0, 3.0), anchor(s, 1).position)
    }

    @Test
    fun penPointsSnapToTheGrid() {
        val s = EditorSession(Document("d", CanvasSize(500.0, 500.0)))
        s.setDrawTool(DrawTool.Pen)
        val view = object : ViewportTransform {
            override val scale = 1.0
            override fun toWorld(screen: Vec2) = screen
        }
        val pen = PenController(s, view, snapProvider = { SnapSettings(gridSize = 10.0) })
        pen.handle(PointerEvent(PointerType.Down, Vec2(13.0, 27.0)))
        pen.handle(PointerEvent(PointerType.Up, Vec2.ZERO))
        assertEquals(Vec2(10.0, 30.0), pen.preview.single().position)
    }

    @Test
    fun pastingCopiesWithNewIdsAndEachPasteLandsFurtherAlong() {
        val s = EditorSession(SampleDocuments.circleButton())
        val clipboard = Clipboard()
        assertNull(s.pasteLayer(clipboard), "nothing copied yet")
        assertTrue(s.copyLayer(layer, clipboard))

        val first = s.pasteLayer(clipboard)
        val second = s.pasteLayer(clipboard)
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(listOf(layer, first, second), s.document.layers.map { it.id })
        assertEquals(Vec2(20.0, 20.0), (s.document.findLayer(first) as ShapeLayer).shape.transform.translation)
        assertEquals(Vec2(40.0, 40.0), (s.document.findLayer(second) as ShapeLayer).shape.transform.translation)
        assertEquals(emptyList(), DocumentInvariants.check(s.document))
        assertEquals(second, s.state.selectedLayer)

        assertTrue(s.undo())
        assertNull(s.document.findLayer(second))
        assertTrue(s.redo())
        assertNotNull(s.document.findLayer(second))
    }

    @Test
    fun theClipboardWorksAcrossDocumentsAndCopyingAgainRestartsTheOffset() {
        val a = EditorSession(SampleDocuments.circleButton())
        val clipboard = Clipboard()
        a.copyLayer(layer, clipboard)

        val b = EditorSession(Document("other", CanvasSize(200.0, 200.0)))
        val pasted = b.pasteLayer(clipboard)
        assertNotNull(pasted)
        assertEquals(1, b.document.layers.size)
        assertEquals(emptyList(), DocumentInvariants.check(b.document))

        b.addShapeLayer("x", Appearance()) { next -> Primitives.circle(Vec2.ZERO, 10.0, next) }
        a.copyLayer(layer, clipboard)
        val again = b.pasteLayer(clipboard)
        assertEquals(Vec2(20.0, 20.0), (b.document.findLayer(again!!) as ShapeLayer).shape.transform.translation)
        assertEquals(false, a.copyLayer(LayerId(999), clipboard))
    }
}
