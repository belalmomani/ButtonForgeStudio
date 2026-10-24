package studio.buttonforge.integration

import studio.buttonforge.document.CanvasSize
import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.appearance.Appearance
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.editorcore.EditorSession
import studio.buttonforge.editorcore.PointModeController
import studio.buttonforge.editorcore.input.PointerEvent
import studio.buttonforge.editorcore.input.PointerType
import studio.buttonforge.editorcore.input.Viewport
import studio.buttonforge.editorcore.points.PointTool
import studio.buttonforge.editorcore.points.TouchSettings
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.primitives.Primitives
import studio.buttonforge.storage.ProjectFiles
import studio.buttonforge.storage.ReadResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The whole path a user takes: create, edit with touch events, save, reopen, keep editing. */
class EditorToStorageTest {
    @Test
    fun touchEditsSurviveSaveAndReopenAndEditingContinues() {
        val session = EditorSession(Document("Untitled", CanvasSize(512.0, 512.0)))
        val layer = session.addShapeLayer("Circle", Appearance()) { next ->
            Primitives.circle(Vec2(256.0, 256.0), 100.0, next)
        }
        session.addPointsToCount(layer, 0, 100)
        session.enterPointMode()

        val viewport = Viewport(scale = 2.0)
        val controller = PointModeController(session, viewport, TouchSettings(hitRadiusPx = 40.0))
        val anchor = (session.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.single().anchors[10]
        val onScreen = viewport.toScreen(anchor.position)
        controller.handle(PointerEvent(PointerType.Down, onScreen))
        controller.handle(PointerEvent(PointerType.Move, onScreen + Vec2(30.0, 10.0)))
        controller.handle(PointerEvent(PointerType.Up, Vec2.ZERO))
        assertEquals(1, session.history.undoSize.let { it - 2 }, "one drag entry after the two earlier edits")

        val path = Files.createTempDirectory("bfs").resolve("p.bfs")
        val files = ProjectFiles()
        files.save(path, session.document)

        val reopened = files.open(path)
        assertIs<ReadResult.Success>(reopened)
        assertEquals(session.document, reopened.document)
        assertEquals(emptyList(), DocumentInvariants.check(reopened.document))

        // the reopened design is fully editable: a new session can move, add and delete points again
        val next = EditorSession(reopened.document)
        next.selectLayer(layer)
        next.enterPointMode()
        next.setPointTool(PointTool.Select)
        val moved = (next.document.findLayer(layer) as ShapeLayer).shape.geometry.contours.single().anchors[10]
        assertEquals(anchor.position.x + 15.0, moved.position.x, 1e-9, "moved by 30/2 world units")
        assertEquals(anchor.position.y + 5.0, moved.position.y, 1e-9, "moved by 10/2 world units")
        assertTrue(next.beginAnchorDrag(layer, setOf(moved.id)))
        next.updateAnchorDrag(Vec2(1.0, 1.0))
        assertTrue(next.commitAnchorDrag())
        assertTrue(next.deleteAnchor(layer, moved.id))
        assertTrue(next.insertAnchorAt(layer, 0, 3, 0.5) != null)
        assertEquals(emptyList(), DocumentInvariants.check(next.document))
        assertEquals(0, next.history.redoSize)
    }

    @Test
    fun historyIsNotStoredInTheFile() {
        val session = EditorSession(Document("Untitled", CanvasSize(100.0, 100.0)))
        session.addShapeLayer("Rect", Appearance()) { next -> Primitives.rectangle(Vec2(50.0, 50.0), 40.0, 20.0, next) }
        val path = Files.createTempDirectory("bfs").resolve("h.bfs")
        ProjectFiles().save(path, session.document)
        val reopened = ProjectFiles().open(path)
        assertIs<ReadResult.Success>(reopened)
        assertEquals(0, EditorSession(reopened.document).history.undoSize)
    }
}
