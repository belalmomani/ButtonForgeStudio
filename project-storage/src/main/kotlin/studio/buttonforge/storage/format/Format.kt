package studio.buttonforge.storage.format

import kotlinx.serialization.json.Json

/** Highest file format version this build can read and the one it writes. */
const val FORMAT_VERSION = 1

internal const val MANIFEST_ENTRY = "manifest.json"
internal const val DOCUMENT_ENTRY = "document.json"
internal const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
internal const val FIXED_ENTRY_TIME = 315532800000L // 1980-01-01, keeps output byte-for-byte reproducible

/** Strict: unknown keys are errors, so a damaged or foreign file is never half-read. */
internal val BfsJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}
