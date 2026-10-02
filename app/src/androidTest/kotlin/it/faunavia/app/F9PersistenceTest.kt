package it.faunavia.app

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F9PersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "f9-persistence.db"
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun wishlistReopensOfflineAndRepeatedAddPreservesOriginalTimestamp() = runBlocking<Unit> {
        var db = FaunaviaDatabase.open(context, name)
        val clock = FakeClock(f8bNow.toEpochMilli())
        try {
            val local = LocalRepositories(db, clock)
            local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
            local.wishlist.add(f8bTaxon.id)
            clock.epochMillis = f8bNow.plusSeconds(60).toEpochMilli()
            local.wishlist.add(f8bTaxon.id)
            assertEquals(listOf(WishlistEntry(f8bTaxon.id, f8bNow)), local.wishlist.list())
            assertTrue(local.diary.list().isEmpty())
            assertThrows(SQLiteConstraintException::class.java) { db.dao().deleteTaxon(f8bTaxon.id) }
            db.close()
            db = FaunaviaDatabase.open(context, name)
            val reopened = LocalRepositories(db, clock)
            assertEquals(f8bNow, reopened.wishlist.list().single().addedAt)
            assertEquals("CC0", reopened.catalogue.taxon(f8bTaxon.id)!!.provenance.license)
            reopened.wishlist.remove(f8bTaxon.id)
            reopened.wishlist.remove(f8bTaxon.id)
            assertTrue(reopened.wishlist.list().isEmpty())
            assertNotNull(reopened.catalogue.taxon(f8bTaxon.id))
        } finally { db.close() }
    }

    @Test fun wishlistRejectsMissingAndInvalidTaxaEvenThroughRawSql() = runBlocking<Unit> {
        val db = FaunaviaDatabase.open(context, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            local.catalogue.saveTaxon(f8bTaxon.copy(id = "plant", kingdom = "Plantae"))
            local.catalogue.saveTaxon(f8bTaxon.copy(id = "synonym", status = TaxonomicStatus.SYNONYM))
            for (id in listOf("missing", "plant", "synonym")) {
                try { local.wishlist.add(id); fail("Invalid wish was accepted") } catch (_: IllegalArgumentException) { }
                assertThrows(SQLiteConstraintException::class.java) { db.dao().addWish(id, f8bNow.toString()) }
            }
            local.catalogue.saveTaxon(f8bTaxon)
            local.wishlist.add(f8bTaxon.id)
            assertThrows(SQLiteConstraintException::class.java) {
                db.openHelper.writableDatabase.execSQL("UPDATE taxa SET status = 'SYNONYM' WHERE id = ?", arrayOf(f8bTaxon.id))
            }
            assertEquals(listOf(f8bTaxon.id), local.wishlist.list().map { it.taxonId })
        } finally { db.close() }
    }

    @Test fun f8bMigrationPreservesDiaryTripAndCuratedSourceWithoutInventingWishes() = runBlocking<Unit> {
        helper.createDatabase(name, 6).apply {
            val row = f8bTaxon.toRow()
            execSQL("INSERT INTO taxa VALUES (?, ?, ?, 'Animalia', 'ACCEPTED', 'SPECIES', 'fixture', 's', 'test', ?, 'CC0', 'Faunavia', 'synthetic', 'v1')",
                arrayOf(row.id, row.scientificName, row.commonName, f8bNow.toString()))
            val trip = f8bTrip().toRow()
            execSQL("INSERT INTO trips VALUES (?, ?)", arrayOf(trip.id, trip.payload))
            execSQL("INSERT INTO observations VALUES ('o', ?, ?, ?, 'Europe/Rome', NULL, NULL, 'preserved', ?, ?, 2, 't', NULL)",
                arrayOf<Any>(f8bTaxon.id, f8bNow.toString(), f8bNow.epochSecond, f8bNow.toString(), f8bNow.toString()))
            execSQL("INSERT INTO suggestion_profiles VALUES (?, 'pilot', '[\"bosco\"]', 0, 0.8, 'reason', 'fixture', 's', 'test', ?, 'CC0', 'Faunavia', 'synthetic', 'v1')", arrayOf(f8bTaxon.id, f8bNow.toString()))
            execSQL("INSERT INTO suggestion_profiles VALUES (?, 'legacy-empty', '[]', 0, 0.8, 'reason', 'fixture', 's', 'test', ?, 'CC0', 'Faunavia', 'synthetic', 'v1')", arrayOf(f8bTaxon.id, f8bNow.toString()))
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, FaunaviaDatabase.MIGRATION_6_7).close()
        val db = FaunaviaDatabase.open(context, name)
        try {
            val local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
            assertTrue(local.wishlist.list().isEmpty())
            assertEquals(f8bTrip(), local.trips.get("t"))
            assertEquals("t", local.diary.get("o")!!.tripId)
            assertEquals(2, local.diary.get("o")!!.quantity)
            val profile = local.catalogue.suggestion(f8bTaxon.id, "pilot")!!
            assertEquals(0, profile.schemaVersion)
            assertEquals("CC0", profile.provenance.license)
            assertTrue(local.catalogue.suggestion(f8bTaxon.id, "legacy-empty")!!.habitats.isEmpty())
            local.wishlist.add(f8bTaxon.id)
            local.trips.delete("t")
            assertEquals(1, local.wishlist.list().size)
            assertNull(local.diary.get("o")!!.tripId)
        } finally { db.close() }
    }

    @Test fun urbanTaxonExcludedFromTypicalStillSavesInDiaryAndWishlistIndependently() = runBlocking<Unit> {
        val db = FaunaviaDatabase.open(context, name)
        try {
            val local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
            local.catalogue.saveTaxon(f8bTaxon)
            local.catalogue.saveSuggestion(SuggestionProfile(f8bTaxon.id, "pilot", listOf("giardino"), true, 0.2, "urban control", f8bProvenance))
            local.wishlist.add(f8bTaxon.id)
            local.diary.create(ObservationDraft("o", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome")))
            local.wishlist.remove(f8bTaxon.id)
            assertNotNull(local.diary.get("o"))
            local.wishlist.add(f8bTaxon.id)
            local.diary.delete("o")
            assertEquals(f8bTaxon.id, local.wishlist.list().single().taxonId)
            assertTrue(local.catalogue.suggestion(f8bTaxon.id, "pilot")!!.urbanCommon)
        } finally { db.close() }
    }
}
