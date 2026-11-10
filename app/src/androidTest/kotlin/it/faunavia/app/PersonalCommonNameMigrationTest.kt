package it.faunavia.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.ObservationDraft
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.testing.FakeClock
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalCommonNameMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "personal-common-name-migration.db"
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun schemaElevenPreservesExistingMemoriesAndMediaWithNoInventedPersonalName() = runBlocking<Unit> {
        helper.createDatabase(name, 11).apply {
            execSQL("INSERT INTO taxa VALUES (?, ?, NULL, 'Animalia', 'ACCEPTED', 'SPECIES', 'fixture', 'record', 'test', ?, 'CC0', 'Faunavia tests', 'synthetic', 'v1')",
                arrayOf(f8bTaxon.id, f8bTaxon.scientificName, f8bNow.toString()))
            execSQL("INSERT INTO observations VALUES ('o', ?, ?, ?, 'UTC', NULL, NULL, 'preserved', ?, ?, 3, NULL, NULL)",
                arrayOf<Any>(f8bTaxon.id, f8bNow.toString(), f8bNow.epochSecond, f8bNow.toString(), f8bNow.toString()))
            execSQL("INSERT INTO observation_photos VALUES ('p', 'o', 'photos/p.jpg', ?, 100, 'image/jpeg', 10, 10, 1, NULL)", arrayOf("a".repeat(64)))
            execSQL("INSERT INTO observation_videos VALUES ('o', 'videos/00000000-0000-0000-0000-000000000001/clip.mp4', ?, 100, 1000, 16, 16)", arrayOf("b".repeat(64)))
            close()
        }
        helper.runMigrationsAndValidate(name, 12, true, FaunaviaDatabase.MIGRATION_11_12).close()
        var db = FaunaviaDatabase.open(context, name)
        try {
            val local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
            val old = requireNotNull(local.diary.get("o"))
            assertNull(old.personalCommonName)
            assertEquals("preserved", old.notes)
            assertEquals(3, old.quantity)
            assertEquals(f8bNow, old.observedAt)
            assertEquals(1, local.diary.photos("o").size)
            assertNotNull(local.videos.get("o"))
            local.diary.update(ObservationDraft("o", f8bTaxon.id, old.observedAt, ZoneId.of("UTC"),
                notes = old.notes, quantity = old.quantity, personalCommonName = "Merlo personale"))
            db.close()
            db = FaunaviaDatabase.open(context, name)
            val reopened = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
            assertEquals("Merlo personale", reopened.diary.get("o")!!.personalCommonName)
            assertEquals(old.createdAt, reopened.diary.get("o")!!.createdAt)
            assertNull(reopened.catalogue.taxon(f8bTaxon.id)!!.commonName)
            assertEquals(1, reopened.diary.photos("o").size)
            assertNotNull(reopened.videos.get("o"))
        } finally { db.close() }
    }
}
