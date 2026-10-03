package it.faunavia.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.exploration.PilotOutingCatalogue
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F17PersistenceTest {
    private val proposal = PilotOutingCatalogue.load().first()
    private val trip = f8bTrip().copy(destination = proposal.place)

    @Test fun guideReopensOfflineWithoutDuplicatesAndLinkedMemoriesSurvivePlanningDeletion() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "f17-persistence.db"
        context.deleteDatabase(name)
        var db = FaunaviaDatabase.open(context, name)
        try {
            var local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
            local.trips.save(trip)
            val outing = proposal.saveFor(trip, trip.startsOn, f8bNow)
            repeat(2) { local.trips.saveOuting(outing) }
            assertEquals(1, local.trips.outings(trip.id).size)
            assertTrue(local.diary.list().isEmpty())
            assertNull(local.catalogue.taxon(f8bTaxon.id))
            local.catalogue.saveTaxon(f8bTaxon)
            local.diary.create(ObservationDraft("sighting", f8bTaxon.id, f8bNow, ZoneId.of("UTC"), tripId = trip.id, outingId = outing.id))
            local.unidentified.save(UnidentifiedInput("draft", f8bNow, ZoneId.of("UTC"), tripId = trip.id, outingId = outing.id))
            db.close(); db = FaunaviaDatabase.open(context, name)
            local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
            assertEquals(outing, local.trips.outing(outing.id))
            assertEquals(proposal.guide.source, local.trips.outing(outing.id)!!.guide!!.source)
            assertEquals(outing.id, local.diary.get("sighting")!!.outingId)
            assertEquals(outing.id, local.unidentified.get("draft")!!.input.outingId)
            local.trips.deleteOuting(outing.id)
            assertEquals(trip.id, local.diary.get("sighting")!!.tripId)
            assertNull(local.diary.get("sighting")!!.outingId)
            assertNull(local.unidentified.get("draft")!!.input.outingId)
            local.trips.delete(trip.id)
            assertNotNull(local.diary.get("sighting")); assertNotNull(local.unidentified.get("draft"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun legacyOutingPayloadRemainsReadableAndInvalidGuideCannotEnterBackupDomain() {
        val saved = proposal.saveFor(trip, trip.startsOn, f8bNow)
        val row = saved.toRow()
        assertEquals(saved, row.toDomain())
        val legacy = JSONObject(row.payload).put("version", 1).apply { remove("guide") }
        assertEquals(saved.copy(guide = null), row.copy(payload = legacy.toString()).toDomain())
        val invalid = JSONObject(row.payload).apply { getJSONObject("guide").put("durationMinutes", -1) }
        assertTrue(runCatching { row.copy(payload = invalid.toString()).toDomain() }.isFailure)
        val unknown = JSONObject(row.payload).put("version", 99)
        assertTrue(runCatching { row.copy(payload = unknown.toString()).toDomain() }.isFailure)
    }
}
