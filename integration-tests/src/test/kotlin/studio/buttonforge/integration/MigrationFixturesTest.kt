package studio.buttonforge.integration

import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.document.layers.ShapeLayer
import studio.buttonforge.geometry.math.Vec2
import studio.buttonforge.storage.ProjectReader
import studio.buttonforge.storage.ProjectWriter
import studio.buttonforge.storage.ReadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Files written by an old version must keep opening. Fixtures are committed once and never regenerated:
 * v1_basic.bfs was written by hand, independently of ProjectWriter.
 */
class MigrationFixturesTest {
    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/projects/$name")) { "missing fixture $name" }.use { it.readBytes() }

    @Test
    fun v1BasicStillOpensWithItsContentIntact() {
        val result = ProjectReader().read(fixture("v1_basic.bfs"))
        assertIs<ReadResult.Success>(result)
        assertNull(result.migratedFrom)
        val doc = result.document
        assertEquals("v1 basic", doc.name)
        assertEquals(512.0, doc.canvas.width)
        assertEquals(emptyList(), DocumentInvariants.check(doc))

        val layer = doc.layers.single() as ShapeLayer
        assertEquals("Square", layer.name)
        val anchors = layer.shape.geometry.contours.single().anchors
        assertEquals(listOf(1L, 2L, 3L, 4L), anchors.map { it.id.value })
        assertEquals(Vec2(100.0, 100.0), anchors[2].position)
        assertEquals(Vec2(10.0, 20.0), layer.shape.transform.translation)
        assertEquals(1, layer.shape.appearance.fills.size)
        assertEquals(1, layer.shape.appearance.strokes.size)
        assertEquals(5L, doc.counters.nextAnchor)
    }

    @Test
    fun anOldFileCanBeResavedWithTheCurrentWriterAndReopenedUnchanged() {
        val first = ProjectReader().read(fixture("v1_basic.bfs"))
        assertIs<ReadResult.Success>(first)
        val second = ProjectReader().read(ProjectWriter().toBytes(first.document))
        assertIs<ReadResult.Success>(second)
        assertEquals(first.document, second.document)
    }
}
