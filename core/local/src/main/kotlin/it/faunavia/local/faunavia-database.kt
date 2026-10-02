package it.faunavia.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaxonRow::class, TaxonAliasRow::class, TaxonPreviewRow::class, SpeciesProfileRow::class,
        SuggestionProfileRow::class, ObservationRow::class, ObservationPhotoRow::class,
        RouteRow::class, SourceEvidenceRow::class, OccurrenceCacheRow::class, AppSettingsRow::class,
        TripRow::class, OutingRow::class, SavedTripPlaceRow::class, SavedTripResultRow::class, UnidentifiedRow::class, WishlistRow::class],
    version = 7,
    exportSchema = true,
)
abstract class FaunaviaDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        // V1 is the initial F2 schema fixture; F1 shipped without a database.
        // Preserve existing settings and use a deterministic zone for legacy rows.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN zoneId TEXT NOT NULL DEFAULT 'UTC'")
                installIntegrity(db)
            }
        }

        /** F3 indexes selected names locally without bundling a global taxonomy snapshot. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `taxon_aliases` (
                        `taxonId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        PRIMARY KEY(`taxonId`, `name`),
                        FOREIGN KEY(`taxonId`) REFERENCES `taxa`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_taxon_aliases_taxonId` ON `taxon_aliases` (`taxonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_taxon_aliases_normalizedName` ON `taxon_aliases` (`normalizedName`)")
                installIntegrity(db)
            }
        }

        /** F4 persists the number of animals while preserving every legacy diary row as one observation. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observations ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
                installIntegrity(db)
            }
        }

        /** F6 persists normalized occurrence cache entries with their TTL and full provenance. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `occurrence_cache` (
                        `key` TEXT NOT NULL,
                        `cachedAt` TEXT NOT NULL,
                        `expiresAt` TEXT NOT NULL,
                        `occurrences` TEXT NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                """.trimIndent())
                installIntegrity(db)
            }
        }

        /** Add nullable links without rebuilding the diary table or cascading its existing photos. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observations ADD COLUMN tripId TEXT")
                db.execSQL("ALTER TABLE observations ADD COLUMN outingId TEXT")
                db.execSQL("CREATE INDEX index_observations_tripId ON observations(tripId)")
                db.execSQL("CREATE INDEX index_observations_outingId ON observations(outingId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS trips (id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id))")
                for (table in listOf("outings", "saved_trip_places")) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS $table (id TEXT NOT NULL, tripId TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX index_${table}_tripId ON $table(tripId)")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_trip_results (id TEXT NOT NULL, tripId TEXT NOT NULL, outingId TEXT, payload TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(outingId) REFERENCES outings(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX index_saved_trip_results_tripId ON saved_trip_results(tripId)")
                db.execSQL("CREATE INDEX index_saved_trip_results_outingId ON saved_trip_results(outingId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS unidentified_drafts (id TEXT NOT NULL, observedAt TEXT NOT NULL, zoneId TEXT NOT NULL, latitude REAL, longitude REAL, notes TEXT NOT NULL, quantity INTEGER NOT NULL, tripId TEXT, outingId TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX index_unidentified_drafts_tripId ON unidentified_drafts(tripId)")
                db.execSQL("CREATE INDEX index_unidentified_drafts_outingId ON unidentified_drafts(outingId)")
                installIntegrity(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE suggestion_profiles ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE suggestion_profiles SET schemaVersion = 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS wishlist (taxonId TEXT NOT NULL, addedAt TEXT NOT NULL, PRIMARY KEY(taxonId), FOREIGN KEY(taxonId) REFERENCES taxa(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                installIntegrity(db)
            }
        }

        fun open(context: Context, name: String = "faunavia.db"): FaunaviaDatabase =
            Room.databaseBuilder(context.applicationContext, FaunaviaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(INTEGRITY_CALLBACK)
                .build()

        val INTEGRITY_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { installIntegrity(db) }
            override fun onOpen(db: SupportSQLiteDatabase) { installIntegrity(db) }
        }

        internal fun installIntegrity(db: SupportSQLiteDatabase) {
            val hasWishlist = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'wishlist'").use { it.moveToFirst() }
            if (hasWishlist) {
                for (operation in listOf("INSERT", "UPDATE")) {
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS wishlist_taxon_${operation.lowercase()}
                        BEFORE $operation ON wishlist
                        WHEN NOT EXISTS (SELECT 1 FROM taxa WHERE id = NEW.taxonId AND kingdom = 'Animalia' AND status = 'ACCEPTED')
                        BEGIN SELECT RAISE(ABORT, 'Wishlist requires accepted Animalia taxon'); END
                    """.trimIndent())
                }
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS protect_wished_taxon BEFORE UPDATE ON taxa
                    WHEN EXISTS (SELECT 1 FROM wishlist WHERE taxonId = OLD.id)
                        AND (NEW.kingdom != 'Animalia' OR NEW.status != 'ACCEPTED')
                    BEGIN SELECT RAISE(ABORT, 'Wishlist taxon must remain accepted Animalia'); END
                """.trimIndent())
            }
            for (operation in listOf("INSERT", "UPDATE")) {
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS observation_taxon_${operation.lowercase()}
                    BEFORE $operation ON observations
                    WHEN length(trim(NEW.taxonId)) = 0 OR NOT EXISTS (SELECT 1 FROM taxa WHERE id = NEW.taxonId
                        AND kingdom = 'Animalia' AND status = 'ACCEPTED')
                    BEGIN SELECT RAISE(ABORT, 'Observation requires accepted Animalia taxon'); END
                """.trimIndent())
            }
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS protect_observed_taxon
                BEFORE UPDATE ON taxa
                WHEN (NEW.kingdom != 'Animalia' OR NEW.status != 'ACCEPTED')
                    AND EXISTS (SELECT 1 FROM observations WHERE taxonId = OLD.id)
                BEGIN SELECT RAISE(ABORT, 'Taxon is referenced by diary'); END
            """.trimIndent())
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'trips'").use {
                if (!it.moveToFirst()) return
            }
            for (table in listOf("observations", "unidentified_drafts", "saved_trip_results")) {
                for (operation in listOf("INSERT", "UPDATE")) {
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS ${table}_link_${operation.lowercase()}
                        BEFORE $operation ON $table
                        WHEN (NEW.tripId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM trips WHERE id = NEW.tripId))
                          OR (NEW.outingId IS NOT NULL AND (NEW.tripId IS NULL OR NOT EXISTS
                            (SELECT 1 FROM outings WHERE id = NEW.outingId AND tripId = NEW.tripId)))
                        BEGIN SELECT RAISE(ABORT, 'Memory requires a matching trip and outing'); END
                    """.trimIndent())
                }
            }
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS unlink_trip_memories BEFORE DELETE ON trips
                BEGIN
                  UPDATE observations SET tripId = NULL, outingId = NULL WHERE tripId = OLD.id;
                  UPDATE unidentified_drafts SET tripId = NULL, outingId = NULL WHERE tripId = OLD.id;
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS unlink_outing_memories BEFORE DELETE ON outings
                BEGIN
                  UPDATE observations SET outingId = NULL WHERE outingId = OLD.id;
                  UPDATE unidentified_drafts SET outingId = NULL WHERE outingId = OLD.id;
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS protect_outing_trip BEFORE UPDATE OF tripId ON outings
                WHEN NEW.tripId != OLD.tripId
                BEGIN SELECT RAISE(ABORT, 'An outing cannot move between trips'); END
            """.trimIndent())
            for ((table, other) in listOf("observations" to "unidentified_drafts", "unidentified_drafts" to "observations")) {
                for (operation in listOf("INSERT", "UPDATE")) db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS ${table}_identity_${operation.lowercase()}
                    BEFORE $operation ON $table
                    WHEN EXISTS (SELECT 1 FROM $other WHERE id = NEW.id)
                    BEGIN SELECT RAISE(ABORT, 'Memory identity already exists'); END
                """.trimIndent())
            }
        }
    }
}
