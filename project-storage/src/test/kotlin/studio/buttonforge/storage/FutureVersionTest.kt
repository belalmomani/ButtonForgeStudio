package studio.buttonforge.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FutureVersionTest {
    private val future = zipOf(
        "manifest.json" to """{"formatVersion":99,"writtenBy":"a newer app"}""",
        "document.json" to """{"somethingNew":true}""",
    )

    @Test
    fun newerFormatIsRejectedWithBothVersions() {
        val r = ProjectReader().read(future)
        assertEquals(ReadResult.Failure(ReadError.UnsupportedFutureVersion(99, 1)), r)
    }

    @Test
    fun rejectedFileIsLeftUntouched() {
        val path = Files.createTempDirectory("bfs").resolve("future.bfs")
        Files.write(path, future)
        val r = ProjectFiles().open(path)
        assertIs<ReadResult.Failure>(r)
        assertContentEquals(future, Files.readAllBytes(path))
    }

    @Test
    fun missingFileIsAnIoError() {
        val r = ProjectFiles().open(Files.createTempDirectory("bfs").resolve("nope.bfs"))
        assertIs<ReadResult.Failure>(r)
        assertIs<ReadError.Io>(r.error)
    }
}
