package studio.buttonforge.storage

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** The only place the storage module touches the file system, so failures can be simulated in tests. */
interface FileOps {
    fun readBytes(path: Path): ByteArray
    fun write(path: Path, bytes: ByteArray)
    fun move(from: Path, to: Path)
    fun copy(from: Path, to: Path)
    fun exists(path: Path): Boolean
    fun deleteIfExists(path: Path)
}

object JavaFileOps : FileOps {
    override fun readBytes(path: Path): ByteArray = Files.readAllBytes(path)

    override fun write(path: Path, bytes: ByteArray) {
        FileChannel.open(
            path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
        ).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    override fun move(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun copy(from: Path, to: Path) {
        Files.copy(from, to)
    }

    override fun exists(path: Path): Boolean = Files.exists(path)

    override fun deleteIfExists(path: Path) {
        Files.deleteIfExists(path)
    }
}
