package studio.buttonforge.storage

import studio.buttonforge.document.Document
import java.io.IOException
import java.nio.file.Path

class SaveException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * Open and save .bfs files. Saving writes a temporary sibling file and renames it over the target, so a failure
 * at any point leaves the previous file intact. After a migration on load, the first save keeps a copy of the
 * original as `<name>.bfs.bak-v<N>`.
 */
class ProjectFiles(
    private val ops: FileOps = JavaFileOps,
    private val reader: ProjectReader = ProjectReader(),
    private val writer: ProjectWriter = ProjectWriter(),
) {
    fun open(path: Path): ReadResult {
        val bytes = try {
            ops.readBytes(path)
        } catch (e: IOException) {
            return ReadResult.Failure(ReadError.Io(e.message ?: "Could not read the file"))
        }
        return reader.read(bytes)
    }

    /** @throws IllegalArgumentException if the document breaks the model invariants (nothing is written). */
    fun save(target: Path, document: Document, migratedFrom: Int? = null) {
        val bytes = writer.toBytes(document)
        val name = target.fileName.toString()
        val temp = target.resolveSibling("$name.tmp")
        try {
            if (migratedFrom != null) {
                val backup = target.resolveSibling("$name.bak-v$migratedFrom")
                if (ops.exists(target) && !ops.exists(backup)) ops.copy(target, backup)
            }
            ops.write(temp, bytes)
            ops.move(temp, target)
        } catch (e: IOException) {
            try {
                ops.deleteIfExists(temp)
            } catch (ignored: IOException) {
            }
            throw SaveException("Could not save $name: ${e.message}", e)
        }
    }
}
