package studio.buttonforge.storage

import studio.buttonforge.document.Document
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.storage.dto.ManifestDto
import studio.buttonforge.storage.format.BfsJson
import studio.buttonforge.storage.format.DOCUMENT_ENTRY
import studio.buttonforge.storage.format.FIXED_ENTRY_TIME
import studio.buttonforge.storage.format.FORMAT_VERSION
import studio.buttonforge.storage.format.MANIFEST_ENTRY
import studio.buttonforge.storage.mapping.toDto
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Serializes to the .bfs archive in memory. Refuses documents that break the model invariants. */
class ProjectWriter(
    private val formatVersion: Int = FORMAT_VERSION,
    private val validate: Boolean = true,
) {
    fun toBytes(document: Document): ByteArray {
        if (validate) {
            val problems = DocumentInvariants.check(document)
            require(problems.isEmpty()) { "Refusing to save an invalid document: $problems" }
        }
        val manifest = BfsJson.encodeToString(ManifestDto.serializer(), ManifestDto(formatVersion, "ButtonForgeStudio"))
        val body = BfsJson.encodeToString(studio.buttonforge.storage.dto.DocumentDto.serializer(), document.toDto())
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, MANIFEST_ENTRY, manifest.encodeToByteArray())
            put(zip, DOCUMENT_ENTRY, body.encodeToByteArray())
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = FIXED_ENTRY_TIME
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }
}
