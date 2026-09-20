package it.faunavia.app

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.occurrence.OccurrenceCacheEntry
import it.faunavia.testing.FakeClock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val name = "f2-migration.db"
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)

    @After fun cleanup() { instrumentation.targetContext.deleteDatabase(name) }

    @Test fun migrationPreservesPopulatedDiaryPhotosAndSettings() = runBlocking<Unit> {
        helper.createDatabase(name, 1).apply {
            execSQL("""
                INSERT INTO taxa VALUES ('fixture:1', 'Turdus merula', 'Merlo', 'Animalia', 'ACCEPTED', 'SPECIES',
                    'fixture', 'record:1', 'local', '2026-09-16T10:00:00Z', 'CC0', 'Faunavia', 'synthetic', 'v1')
            """.trimIndent())
            execSQL("""
                INSERT INTO observations VALUES ('o', 'fixture:1', '2026-09-16T10:00:00Z', 1789552800,
                    'Europe/Rome', 45.5, 9.2, 'preserved note', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z')
            """.trimIndent())
            execSQL("INSERT INTO observation_photos VALUES ('p', 'o', 'photos/p.jpg', ?, 100, 'image/jpeg')", arrayOf("a".repeat(64)))
            execSQL("INSERT INTO app_settings VALUES (1, 1, 1275)")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, FaunaviaDatabase.MIGRATION_1_2, FaunaviaDatabase.MIGRATION_2_3, FaunaviaDatabase.MIGRATION_3_4, FaunaviaDatabase.MIGRATION_4_5).apply {
            assertThrows(SQLiteConstraintException::class.java) {
                execSQL("UPDATE observations SET taxonId = NULL WHERE id = 'o'")
            }
            close()
        }
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertEquals("preserved note", local.diary.get("o")!!.notes)
            assertEquals(1, local.diary.get("o")!!.quantity)
            assertEquals(Instant.parse("2026-09-16T10:00:00Z"), local.diary.get("o")!!.observedAt)
            assertEquals("p", local.diary.photos("o").single().id)
            assertEquals("CC0", local.catalogue.taxon("fixture:1")!!.provenance.license)
            assertTrue(local.catalogue.aliases("fixture:1").isEmpty())
            assertTrue(local.settings.get().reminderEnabled)
            assertEquals(LocalTime.of(21, 15), local.settings.get().reminderTime)
            assertEquals(ZoneId.of("UTC"), local.settings.get().zoneId)
            assertNull(local.occurrenceCache.read("missing"))
            assertThrows(SQLiteConstraintException::class.java) {
                db.openHelper.writableDatabase.execSQL("UPDATE taxa SET status = 'SYNONYM' WHERE id = 'fixture:1'")
            }
        } finally { db.close() }
    }

    @Test fun emptyInitialSchemaMigratesWithoutInventingData() = runBlocking<Unit> {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 5, true, FaunaviaDatabase.MIGRATION_1_2, FaunaviaDatabase.MIGRATION_2_3, FaunaviaDatabase.MIGRATION_3_4, FaunaviaDatabase.MIGRATION_4_5).close()
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertTrue(local.diary.list().isEmpty())
            assertFalse(local.settings.get().reminderEnabled)
        } finally { db.close() }
    }

    @Test fun migrationAddsAliasIndexWithoutLosingExistingTaxon() = runBlocking<Unit> {
        helper.createDatabase(name, 2).apply {
            execSQL("""
                INSERT INTO taxa VALUES ('fixture:1', 'Turdus merula', 'Merlo', 'Animalia', 'ACCEPTED', 'SPECIES',
                    'fixture', 'record:1', 'local', '2026-09-16T10:00:00Z', 'CC0', 'Faunavia', 'synthetic', 'v2')
            """.trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, FaunaviaDatabase.MIGRATION_2_3, FaunaviaDatabase.MIGRATION_3_4, FaunaviaDatabase.MIGRATION_4_5).close()
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertEquals("Turdus merula", local.catalogue.taxon("fixture:1")!!.scientificName)
            local.catalogue.saveTaxonWithAliases(local.catalogue.taxon("fixture:1")!!, listOf("Merula nigra"))
            assertEquals(listOf("fixture:1"), local.catalogue.searchTaxa("nigra", 8).map { it.id })
        } finally { db.close() }
    }

    @Test fun migrationFromF3DefaultsLegacyQuantityToOne() = runBlocking<Unit> {
        helper.createDatabase(name, 3).apply {
            execSQL("""
                INSERT INTO taxa VALUES ('fixture:1', 'Turdus merula', 'Merlo', 'Animalia', 'ACCEPTED', 'SPECIES',
                    'fixture', 'record:1', 'local', '2026-09-16T10:00:00Z', 'CC0', 'Faunavia', 'synthetic', 'v3')
            """.trimIndent())
            execSQL("""
                INSERT INTO observations VALUES ('o', 'fixture:1', '2026-09-16T10:00:00Z', 1789552800,
                    'Europe/Rome', NULL, NULL, '', '2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z')
            """.trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, FaunaviaDatabase.MIGRATION_3_4, FaunaviaDatabase.MIGRATION_4_5).close()
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            assertEquals(1, LocalRepositories(db, FakeClock(0)).diary.get("o")!!.quantity)
        } finally { db.close() }
    }

    @Test fun migrationFromF4CreatesEmptyOccurrenceCache() = runBlocking<Unit> {
        helper.createDatabase(name, 4).close()
        helper.runMigrationsAndValidate(name, 5, true, FaunaviaDatabase.MIGRATION_4_5).close()
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertNull(local.occurrenceCache.read("missing"))
            local.occurrenceCache.save(OccurrenceCacheEntry("empty", Instant.EPOCH, Instant.EPOCH, emptyList()))
            assertTrue(local.occurrenceCache.read("empty")!!.occurrences.isEmpty())
        } finally { db.close() }
    }
}
