package studio.buttonforge.storage

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.kitchenSink
import studio.buttonforge.document.layers.LayerId
import studio.buttonforge.document.layers.renameLayer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SaveFailureTest {
    private val doc1 = SampleDocuments.kitchenSink()
    private val doc2 = doc1.renameLayer(LayerId(4), "Renamed")

    private fun savedProject(): Triple<java.nio.file.Path, java.nio.file.Path, ByteArray> {
        val dir = Files.createTempDirectory("bfs")
        val target = dir.resolve("p.bfs")
        ProjectFiles().save(target, doc1)
        return Triple(dir, target, Files.readAllBytes(target))
    }

    @Test
    fun failureWhileWritingKeepsTheOriginalAndCleansUp() {
        val (dir, target, original) = savedProject()
        assertFailsWith<SaveException> { ProjectFiles(FailingFileOps(failWrite = true)).save(target, doc2) }
        assertContentEquals(original, Files.readAllBytes(target))
        assertFalse(Files.exists(dir.resolve("p.bfs.tmp")))
        val r = ProjectFiles().open(target)
        assertIs<ReadResult.Success>(r)
        assertEquals(doc1, r.document)
    }

    @Test
    fun failureWhileRenamingKeepsTheOriginalAndCleansUp() {
        val (dir, target, original) = savedProject()
        assertFailsWith<SaveException> { ProjectFiles(FailingFileOps(failMove = true)).save(target, doc2) }
        assertContentEquals(original, Files.readAllBytes(target))
        assertFalse(Files.exists(dir.resolve("p.bfs.tmp")))
    }

    @Test
    fun invalidDocumentIsRefusedBeforeAnythingIsWritten() {
        val (dir, target, original) = savedProject()
        val broken = doc1.copy(counters = studio.buttonforge.document.IdCounters(1, 1))
        assertFailsWith<IllegalArgumentException> { ProjectFiles().save(target, broken) }
        assertContentEquals(original, Files.readAllBytes(target))
        assertFalse(Files.exists(dir.resolve("p.bfs.tmp")))
    }

    @Test
    fun successfulSaveReplacesTheFile() {
        val (_, target, _) = savedProject()
        ProjectFiles().save(target, doc2)
        val r = ProjectFiles().open(target)
        assertIs<ReadResult.Success>(r)
        assertEquals(doc2, r.document)
    }
}
