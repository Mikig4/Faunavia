package it.faunavia.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaxonRow::class, TaxonPreviewRow::class, SpeciesProfileRow::class,
        SuggestionProfileRow::class, ObservationRow::class, ObservationPhotoRow::class,
        RouteRow::class, SourceEvidenceRow::class, AppSettingsRow::class],
    version = 2,
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

        fun open(context: Context, name: String = "faunavia.db"): FaunaviaDatabase =
            Room.databaseBuilder(context.applicationContext, FaunaviaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .addCallback(INTEGRITY_CALLBACK)
                .build()

        val INTEGRITY_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { installIntegrity(db) }
            override fun onOpen(db: SupportSQLiteDatabase) { installIntegrity(db) }
        }

        internal fun installIntegrity(db: SupportSQLiteDatabase) {
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
        }
    }
}
