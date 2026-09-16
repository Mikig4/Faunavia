package it.faunavia.app

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
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
        helper.runMigrationsAndValidate(name, 2, true, FaunaviaDatabase.MIGRATION_1_2).apply {
            assertThrows(SQLiteConstraintException::class.java) {
                execSQL("UPDATE observations SET taxonId = NULL WHERE id = 'o'")
            }
            close()
        }
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertEquals("preserved note", local.diary.get("o")!!.notes)
            assertEquals(Instant.parse("2026-09-16T10:00:00Z"), local.diary.get("o")!!.observedAt)
            assertEquals("p", local.diary.photos("o").single().id)
            assertEquals("CC0", local.catalogue.taxon("fixture:1")!!.provenance.license)
            assertTrue(local.settings.get().reminderEnabled)
            assertEquals(LocalTime.of(21, 15), local.settings.get().reminderTime)
            assertEquals(ZoneId.of("UTC"), local.settings.get().zoneId)
            assertThrows(SQLiteConstraintException::class.java) {
                db.openHelper.writableDatabase.execSQL("UPDATE taxa SET status = 'SYNONYM' WHERE id = 'fixture:1'")
            }
        } finally { db.close() }
    }

    @Test fun emptyInitialSchemaMigratesWithoutInventingData() = runBlocking<Unit> {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 2, true, FaunaviaDatabase.MIGRATION_1_2).close()
        val db = FaunaviaDatabase.open(instrumentation.targetContext, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertTrue(local.diary.list().isEmpty())
            assertFalse(local.settings.get().reminderEnabled)
        } finally { db.close() }
    }
}
