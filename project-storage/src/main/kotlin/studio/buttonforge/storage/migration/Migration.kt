package studio.buttonforge.storage.migration

import kotlinx.serialization.json.JsonObject

/** Upgrades the raw document JSON from version [from] to [to] before it is decoded with the current schema. */
interface Migration {
    val from: Int
    val to: Int
    fun migrate(document: JsonObject): JsonObject
}

/** Migrations shipped with the app. Empty while the format is still at version 1. */
object BuiltInMigrations {
    val all: List<Migration> = emptyList()
}
