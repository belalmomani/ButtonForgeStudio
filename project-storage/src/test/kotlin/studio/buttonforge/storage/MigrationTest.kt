package studio.buttonforge.storage

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import studio.buttonforge.document.SampleDocuments
import studio.buttonforge.storage.migration.Migration
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationTest {
    private val v1Bytes = ProjectWriter().toBytes(SampleDocuments.circleButton())

    private val renaming = object : Migration {
        override val from = 1
        override val to = 2
        override fun migrate(document: JsonObject) = JsonObject(document + ("name" to JsonPrimitive("MIGRATED")))
    }

    @Test
    fun migrationIsAppliedAndReported() {
        val r = ProjectReader(currentVersion = 2, migrations = listOf(renaming)).read(v1Bytes)
        assertIs<ReadResult.Success>(r)
        assertEquals("MIGRATED", r.document.name)
        assertEquals(1, r.migratedFrom)
    }

    @Test
    fun missingStepIsReported() {
        val r = ProjectReader(currentVersion = 3, migrations = listOf(renaming)).read(v1Bytes)
        assertEquals(ReadResult.Failure(ReadError.MissingMigration(2)), r)
    }

    @Test
    fun withoutAnyMigrationAnOldFileIsReportedNotGuessed() {
        val r = ProjectReader(currentVersion = 2, migrations = emptyList()).read(v1Bytes)
        assertEquals(ReadResult.Failure(ReadError.MissingMigration(1)), r)
    }

    @Test
    fun firstSaveAfterMigrationKeepsAnUntouchedBackupOfTheOriginal() {
        val dir = Files.createTempDirectory("bfs")
        val path = dir.resolve("old.bfs")
        Files.write(path, v1Bytes)
        val files = ProjectFiles(reader = ProjectReader(currentVersion = 2, migrations = listOf(renaming)))

        val opened = files.open(path)
        assertIs<ReadResult.Success>(opened)
        files.save(path, opened.document, migratedFrom = opened.migratedFrom)

        val backup = dir.resolve("old.bfs.bak-v1")
        assertTrue(Files.exists(backup))
        assertContentEquals(v1Bytes, Files.readAllBytes(backup))

        // a later save must not overwrite the backup
        files.save(path, opened.document.copy(name = "again"), migratedFrom = 1)
        assertContentEquals(v1Bytes, Files.readAllBytes(backup))
        assertFalse(Files.exists(dir.resolve("old.bfs.tmp")))
    }
}
