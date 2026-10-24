package studio.buttonforge.storage

import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.document.kitchenSink
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CorruptFileTest {
    private fun failure(bytes: ByteArray): ReadError {
        val r = ProjectReader().read(bytes)
        assertIs<ReadResult.Failure>(r)
        return r.error
    }

    private fun documentJson(): String {
        val bytes = ProjectWriter().toBytes(SampleDocuments.kitchenSink())
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name == "document.json") return zin.readBytes().decodeToString()
            }
        }
        error("document.json not found")
    }

    private fun withDocument(json: String) = zipOf("manifest.json" to VALID_MANIFEST, "document.json" to json)

    @Test
    fun randomBytesAreNotAProject() {
        assertIs<ReadError.NotAProject>(failure(Random(1).nextBytes(500)))
        assertIs<ReadError.NotAProject>(failure(ByteArray(0)))
    }

    @Test
    fun truncatedArchiveIsRejected() {
        val bytes = ProjectWriter().toBytes(SampleDocuments.kitchenSink())
        val error = failure(bytes.copyOf(bytes.size / 2))
        assertTrue(error is ReadError.NotAProject || error is ReadError.MissingEntry || error is ReadError.Corrupt, "$error")
    }

    @Test
    fun missingEntriesAreNamed() {
        assertEquals(ReadError.MissingEntry("manifest.json"), failure(zipOf("document.json" to "{}")))
        assertEquals(ReadError.MissingEntry("document.json"), failure(zipOf("manifest.json" to VALID_MANIFEST)))
    }

    @Test
    fun brokenJsonIsCorrupt() {
        assertIs<ReadError.Corrupt>(failure(withDocument("{not json")))
        assertIs<ReadError.Corrupt>(failure(withDocument("[]")))
        assertIs<ReadError.Corrupt>(failure(withDocument("{}"))) // required fields missing
        assertIs<ReadError.Corrupt>(failure(zipOf("manifest.json" to "{}", "document.json" to "{}")))
    }

    @Test
    fun nanIsCorrupt() {
        val json = documentJson()
        assertTrue(json.contains("\"width\":640.5"))
        assertIs<ReadError.Corrupt>(failure(withDocument(json.replace("\"width\":640.5", "\"width\":NaN"))))
    }

    @Test
    fun unknownNamesAreCorrupt() {
        val json = documentJson()
        assertTrue(json.contains("\"corner\":\"Smooth\""))
        assertIs<ReadError.Corrupt>(failure(withDocument(json.replaceFirst("\"corner\":\"Smooth\"", "\"corner\":\"Wobbly\""))))
        assertIs<ReadError.Corrupt>(failure(withDocument(json.replaceFirst("\"type\":\"solid\"", "\"type\":\"mystery\""))))
    }

    @Test
    fun valuesTheModelRejectsAreInvalid() {
        val json = documentJson()
        assertTrue(json.contains("\"blur\":7.25"))
        assertIs<ReadError.Invalid>(failure(withDocument(json.replace("\"blur\":7.25", "\"blur\":-1.0"))))
    }

    @Test
    fun brokenReferencesAreInvalid() {
        val json = documentJson()
        // a state override pointing at a layer that does not exist
        assertTrue(json.contains("\"layerId\":3"))
        val result = failure(withDocument(json.replaceFirst("\"layerId\":3", "\"layerId\":99")))
        assertIs<ReadError.Invalid>(result)
        assertTrue(result.problems.any { it.contains("missing layer 99") }, result.problems.toString())
    }

    @Test
    fun duplicateAnchorIdsAreInvalid() {
        val json = documentJson()
        assertTrue(json.contains("\"id\":7001"))
        val result = failure(withDocument(json.replaceFirst("\"id\":7001", "\"id\":7000")))
        assertTrue(result is ReadError.Invalid || result is ReadError.Corrupt, "$result")
    }
}
