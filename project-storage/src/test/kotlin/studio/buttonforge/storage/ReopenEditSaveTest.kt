package studio.buttonforge.storage

import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.IdAllocator
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.kitchenSink
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.document.layers.findLayer
import studio.buttonforge.document.layers.updateShape
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.geometry.path.CompoundPath
import studio.buttonforge.geometry.path.SegmentParam
import studio.buttonforge.geometry.path.withInsertedAnchors
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReopenEditSaveTest {
    @Test
    fun savedProjectCanBeReopenedEditedSavedAndReopened() {
        val dir = Files.createTempDirectory("bfs")
        val path = dir.resolve("project.bfs")
        val files = ProjectFiles()
        files.save(path, SampleDocuments.kitchenSink())

        val first = files.open(path)
        assertIs<ReadResult.Success>(first)
        val loaded = first.document
        val alloc = IdAllocator(loaded.counters)
        val oldMaxAnchor = loaded.counters.nextAnchor - 1

        var movedIds = emptyList<Long>()
        val edited = loaded.updateShape(LayerId(2)) { shape ->
            var c = shape.geometry.contours.first()
            val anchors = c.anchors.toMutableList()
            // move three points, change a handle, delete one point
            for (i in 10..12) anchors[i] = anchors[i].copy(position = anchors[i].position + Vec2(5.0, -3.0))
            movedIds = (10..12).map { anchors[it].id.value }
            anchors[0] = anchors[0].copy(handleOut = anchors[0].handleOut + Vec2(1.0, 1.0))
            anchors.removeAt(50)
            c = c.copy(anchors = anchors)
            // insert two points that keep the shape
            c = c.withInsertedAnchors(listOf(SegmentParam(0, 0.5), SegmentParam(20, 0.25)), alloc::nextAnchorId)
            shape.copy(geometry = CompoundPath(listOf(c) + shape.geometry.contours.drop(1), shape.geometry.fillRule))
        }.copy(counters = alloc.counters())

        files.save(path, edited)
        val second = files.open(path)
        assertIs<ReadResult.Success>(second)
        assertEquals(edited, second.document)
        assertEquals(emptyList(), DocumentInvariants.check(second.document))

        val contour = (second.document.findLayer(LayerId(2)) as ShapeLayer).shape.geometry.contours.first()
        val newIds = contour.anchors.map { it.id.value }.filter { it > oldMaxAnchor }
        assertEquals(2, newIds.size)
        assertEquals(newIds.size, newIds.toSet().size)
        assertTrue(movedIds.all { id -> contour.anchors.any { it.id.value == id } }, "moved anchors keep their ids")
        assertTrue(contour.anchors.none { it.id.value == 0L })
    }
}
