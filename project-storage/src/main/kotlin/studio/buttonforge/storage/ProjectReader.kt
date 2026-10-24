package studio.buttonforge.storage

import kotlinx.serialization.json.JsonObject
import studio.buttonforge.document.DocumentInvariants
import studio.buttonforge.storage.dto.DocumentDto
import studio.buttonforge.storage.dto.ManifestDto
import studio.buttonforge.storage.format.BfsJson
import studio.buttonforge.storage.format.DOCUMENT_ENTRY
import studio.buttonforge.storage.format.FORMAT_VERSION
import studio.buttonforge.storage.format.MANIFEST_ENTRY
import studio.buttonforge.storage.format.MAX_TOTAL_BYTES
import studio.buttonforge.storage.mapping.FormatProblem
import studio.buttonforge.storage.mapping.toModel
import studio.buttonforge.storage.migration.BuiltInMigrations
import studio.buttonforge.storage.migration.Migration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Never throws for bad input: every problem comes back as [ReadResult.Failure]. */
class ProjectReader(
    private val currentVersion: Int = FORMAT_VERSION,
    private val migrations: List<Migration> = BuiltInMigrations.all,
) {
    private class TooLarge : RuntimeException()

    private fun fail(error: ReadError): ReadResult = ReadResult.Failure(error)

    fun read(bytes: ByteArray): ReadResult {
        val entries = try {
            unzip(bytes)
        } catch (e: TooLarge) {
            return fail(ReadError.Corrupt("Archive is too large"))
        } catch (e: IOException) {
            return fail(ReadError.NotAProject(e.message ?: "Unreadable archive"))
        }
        if (entries.isEmpty()) return fail(ReadError.NotAProject("Not a project archive"))
        val manifestBytes = entries[MANIFEST_ENTRY] ?: return fail(ReadError.MissingEntry(MANIFEST_ENTRY))
        val documentBytes = entries[DOCUMENT_ENTRY] ?: return fail(ReadError.MissingEntry(DOCUMENT_ENTRY))

        val manifest = try {
            BfsJson.decodeFromString(ManifestDto.serializer(), manifestBytes.decodeToString())
        } catch (e: IllegalArgumentException) {
            return fail(ReadError.Corrupt("manifest: ${e.message}"))
        }
        if (manifest.formatVersion > currentVersion) {
            return fail(ReadError.UnsupportedFutureVersion(manifest.formatVersion, currentVersion))
        }
        if (manifest.formatVersion < 1) return fail(ReadError.Corrupt("Invalid format version ${manifest.formatVersion}"))

        val parsed = try {
            BfsJson.parseToJsonElement(documentBytes.decodeToString())
        } catch (e: IllegalArgumentException) {
            return fail(ReadError.Corrupt("document: ${e.message}"))
        }
        var json: JsonObject = parsed as? JsonObject ?: return fail(ReadError.Corrupt("document: expected a JSON object"))

        var version = manifest.formatVersion
        while (version < currentVersion) {
            val step = migrations.firstOrNull { it.from == version } ?: return fail(ReadError.MissingMigration(version))
            if (step.to <= step.from) return fail(ReadError.Corrupt("Broken migration from $version"))
            json = step.migrate(json)
            version = step.to
        }

        val dto = try {
            BfsJson.decodeFromJsonElement(DocumentDto.serializer(), json)
        } catch (e: IllegalArgumentException) {
            return fail(ReadError.Corrupt("document: ${e.message}"))
        }
        val document = try {
            dto.toModel()
        } catch (e: FormatProblem) {
            return fail(ReadError.Corrupt(e.message ?: "Invalid content"))
        } catch (e: IllegalArgumentException) {
            return fail(ReadError.Invalid(listOf(e.message ?: "Invalid value")))
        }
        val problems = DocumentInvariants.check(document)
        if (problems.isNotEmpty()) return fail(ReadError.Invalid(problems))
        return ReadResult.Success(document, if (manifest.formatVersion < currentVersion) manifest.formatVersion else null)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = zin.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_TOTAL_BYTES) throw TooLarge()
                    out.write(buffer, 0, n)
                }
                if (result.put(entry.name, out.toByteArray()) != null) throw IOException("Duplicate entry ${entry.name}")
            }
        }
        return result
    }
}
