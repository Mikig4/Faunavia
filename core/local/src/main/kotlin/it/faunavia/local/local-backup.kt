package it.faunavia.local

import android.content.Context
import android.database.Cursor
import it.faunavia.domain.ObservationPhoto
import it.faunavia.domain.PersonalModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Callable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LocalBackupSnapshot(val payload: String, val counts: Map<String, Int>, val photos: List<ObservationPhoto>,
    val models: List<PersonalModel> = emptyList())

/** Row snapshots avoid closing a live Room connection or copying an uncheckpointed WAL. */
class LocalBackupStore(private val database: FaunaviaDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onReplacement: () -> Unit = {}) {
    companion object {
        const val SCHEMA = 10
        const val MAX_PAYLOAD_BYTES = 64L * 1024 * 1024
        private const val MAX_ROWS = 100_000
        // Parents precede children. Reverse order also respects the custom planning unlink triggers.
        val TABLES = listOf("taxa", "taxon_aliases", "taxon_previews", "species_profiles", "suggestion_profiles",
            "wishlist", "source_evidence", "routes", "occurrence_cache", "app_settings", "trips", "outings",
            "saved_trip_places", "saved_trip_results", "observations", "unidentified_drafts",
            "observation_photos", "draft_photos", "daily_summary_deliveries", "personal_models")
        fun tablesFor(schema: Int): List<String> = when (schema) {
            8 -> TABLES.dropLast(2)
            9 -> TABLES.dropLast(1)
            10 -> TABLES
            else -> error("Unsupported database backup version.")
        }
    }

    suspend fun snapshot(): LocalBackupSnapshot = withContext(io) {
        database.runInTransaction(Callable { capture() })
    }

    /** The first archive contract accepts the immediately preceding photo-capable Room schema too. */
    suspend fun validate(context: Context, payload: String): LocalBackupSnapshot = withContext(io) {
        val name = "backup-stage-${UUID.randomUUID()}.db"
        val stage = FaunaviaDatabase.open(context, name)
        try {
            val store = LocalBackupStore(stage, io)
            store.replace(payload)
            store.snapshot()
        } finally {
            stage.close()
            check(context.deleteDatabase(name)) { "Cannot clean backup staging database." }
        }
    }

    /** All existing connections and repositories survive; any exception rolls the replacement back. */
    suspend fun replace(payload: String, beforeCommit: () -> Unit = {}) = withContext(io) {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
        val root = JSONObject(payload)
        val schema = root.getInt("schemaVersion")
        require(schema in 8..SCHEMA) { "Unsupported database backup version." }
        val tables = root.getJSONObject("tables")
        val expected = tablesFor(schema)
        require(tables.keys().asSequence().toSet() == expected.toSet()) { "Incomplete database inventory." }
        database.runInTransaction {
            val sql = database.openHelper.writableDatabase
            TABLES.asReversed().forEach { sql.execSQL("DELETE FROM `$it`") }
            var totalRows = 0
            expected.forEach { table ->
                val saved = tables.getJSONObject(table)
                val columns = saved.getJSONArray("columns")
                val definition = sql.query("PRAGMA table_info(`$table`)").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(1), cursor.getString(2), cursor.getInt(3) != 0)) }
                }
                require((0 until columns.length()).map(columns::getString) == definition.map { it.first })
                val rows = saved.getJSONArray("rows")
                totalRows += rows.length()
                require(totalRows <= MAX_ROWS) { "Backup contains too many records." }
                val insert = "INSERT INTO `$table` (${definition.joinToString { "`${it.first}`" }}) VALUES (${definition.joinToString { "?" }})"
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONArray(index)
                    require(row.length() == definition.size)
                    val values = Array<Any?>(definition.size) { column ->
                        val value = row.get(column)
                        val (_, type, required) = definition[column]
                        when {
                            value === JSONObject.NULL -> { require(!required); null }
                            type == "TEXT" -> { require(value is String); value }
                            type == "INTEGER" -> { require(value is Int || value is Long); value }
                            type == "REAL" -> { require(value is Number && value.toDouble().isFinite()); value }
                            else -> error("Unsupported backup column type.")
                        }
                    }
                    sql.execSQL(insert, values)
                }
            }
            validateDomain()
            sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
            sql.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok") }
            beforeCommit()
            onReplacement()
        }
    }

    private fun capture(): LocalBackupSnapshot {
        validateDomain()
        val tables = JSONObject()
        val counts = linkedMapOf<String, Int>()
        var total = 0
        TABLES.forEach { table ->
            database.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { cursor ->
                val rows = JSONArray()
                while (cursor.moveToNext()) {
                    require(++total <= MAX_ROWS)
                    val row = JSONArray()
                    for (column in 0 until cursor.columnCount) row.put(when (cursor.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(column)
                        else -> error("Unsupported backup value.")
                    })
                    rows.put(row)
                }
                counts[table] = rows.length()
                tables.put(table, JSONObject().put("columns", JSONArray(cursor.columnNames.toList())).put("rows", rows))
            }
        }
        val payload = JSONObject().put("schemaVersion", SCHEMA).put("tables", tables).toString()
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
        val dao = database.dao()
        return LocalBackupSnapshot(payload, counts, dao.allPhotos().map { it.toDomain() } + dao.allDraftPhotos().map { it.toDomain() },
            dao.allPersonalModels().map { it.toDomain() })
    }

    private fun validateDomain() {
        val dao = database.dao()
        val sql = database.openHelper.writableDatabase
        sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
        sql.query("SELECT id FROM app_settings WHERE id != 1 OR reminderEnabled NOT IN (0, 1)").use { require(!it.moveToFirst()) }
        sql.query("SELECT taxonId FROM suggestion_profiles WHERE urbanCommon NOT IN (0, 1)").use { require(!it.moveToFirst()) }
        for (table in listOf("observations", "unidentified_drafts", "source_evidence")) {
            sql.query("SELECT id FROM `$table` WHERE (latitude IS NULL) != (longitude IS NULL)").use { require(!it.moveToFirst()) }
        }
        for (table in listOf("observations", "unidentified_drafts", "saved_trip_results")) {
            sql.query("SELECT id FROM `$table` WHERE (tripId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM trips WHERE trips.id = `$table`.tripId)) OR (outingId IS NOT NULL AND (tripId IS NULL OR NOT EXISTS (SELECT 1 FROM outings WHERE outings.id = `$table`.outingId AND outings.tripId = `$table`.tripId)))")
                .use { require(!it.moveToFirst()) }
        }
        dao.allTaxa().forEach { it.toDomain() }
        dao.allAliases().forEach { it.toDomain() }
        dao.allPreviews().forEach { it.toDomain() }
        dao.allProfiles().forEach { it.toDomain() }
        dao.allSuggestions().forEach { it.toDomain() }
        dao.wishes().forEach { require(it.taxonId.isNotBlank()); Instant.parse(it.addedAt) }
        dao.allEvidence().forEach { it.toDomain() }
        dao.allOccurrenceCache().forEach { it.toDomain() }
        dao.routes().forEach { it.toDomain() }
        dao.observations().forEach { require(it.toDomain().observedAt.epochSecond == it.observedEpochSecond) }
        dao.unidentifiedDrafts().forEach { it.toDomain() }
        dao.allPhotos().forEach { it.toDomain() }
        dao.allDraftPhotos().forEach { it.toDomain() }
        val trips = dao.trips().map { it.toDomain() }.associateBy { it.id }
        dao.allOutings().forEach { row ->
            val outing = row.toDomain()
            val trip = requireNotNull(trips[outing.tripId])
            require(outing.date in trip.startsOn..trip.endsOn)
        }
        dao.allTripPlaces().forEach { it.toDomain() }
        dao.allTripResults().forEach { it.toDomain() }
        dao.settings()?.let { require(it.id == 1); it.toDomain() }
        dao.allDeliveries().forEach { LocalDate.parse(it.date); ZoneId.of(it.zoneId); Instant.parse(it.notifiedAt) }
        dao.allPersonalModels().forEach { it.toDomain() }
    }
}
