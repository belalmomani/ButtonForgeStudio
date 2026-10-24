package studio.buttonforge.storage

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun zipOf(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, text) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.encodeToByteArray())
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

const val VALID_MANIFEST = """{"formatVersion":1,"writtenBy":"test"}"""

/** Simulates a full disk (half the bytes written, then an error) or a failed rename. */
class FailingFileOps(
    private val real: FileOps = JavaFileOps,
    private val failWrite: Boolean = false,
    private val failMove: Boolean = false,
) : FileOps by real {
    override fun write(path: Path, bytes: ByteArray) {
        if (failWrite) {
            real.write(path, bytes.copyOf(bytes.size / 2))
            throw IOException("disk full")
        }
        real.write(path, bytes)
    }

    override fun move(from: Path, to: Path) {
        if (failMove) throw IOException("rename failed")
        real.move(from, to)
    }
}
