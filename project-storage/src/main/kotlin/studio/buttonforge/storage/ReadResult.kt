package studio.buttonforge.storage

import studio.buttonforge.document.Document

sealed interface ReadError {
    data class NotAProject(val detail: String) : ReadError
    data class MissingEntry(val name: String) : ReadError
    data class Corrupt(val detail: String) : ReadError
    data class UnsupportedFutureVersion(val found: Int, val supported: Int) : ReadError
    data class MissingMigration(val from: Int) : ReadError
    data class Invalid(val problems: List<String>) : ReadError
    data class Io(val detail: String) : ReadError
}

sealed interface ReadResult {
    /** [migratedFrom] is the file's original version when it was upgraded on load, otherwise null. */
    data class Success(val document: Document, val migratedFrom: Int?) : ReadResult
    data class Failure(val error: ReadError) : ReadResult
}
