package it.faunavia.app

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.testing.FakeClock
import it.faunavia.exploration.ExplorationResult
import it.faunavia.plausibility.AnalysisPeriod
import it.faunavia.taxonomy.*
import org.json.JSONObject
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "f8b-persistence.db"
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private val clock = FakeClock(f8bNow.toEpochMilli())
    private val zone = ZoneId.of("Europe/Rome")
    private val trip = f8bTrip()
    private val outing = Outing("out", trip.id, "Parco", trip.startsOn, trip.destination)
    private fun observation(id: String = "o", tripId: String? = trip.id, outingId: String? = outing.id) =
        ObservationDraft(id, f8bTaxon.id, f8bNow, zone, null, "Ricordo", 2, tripId, outingId)
    private fun draft(id: String = "d", tripId: String? = trip.id, outingId: String? = outing.id) =
        UnidentifiedInput(id, f8bNow, zone, GeoPoint(45.0, 9.0), "Da identificare", 3, tripId, outingId)
    private fun savedResult() = SavedTripResult("r", trip.id, outing.id, f8bTaxon.id, f8bTaxon.scientificName,
        EvidenceLevel.DOCUMENTED, listOf("Osservazione storica, non presenza garantita"), "Stagione non disponibile",
        listOf(SourceEvidence("source", f8bTaxon.id, EvidenceLevel.DOCUMENTED, f8bNow, GeoPoint(45.0, 9.0), 20.0,
            "Fonte esterna", f8bProvenance)), tripAnalysisKey(trip, outing), f8bNow, partial = true, stale = true,
        startsOn = outing.date, endsOn = outing.date, area = outing.place)

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name)
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
        local.catalogue.saveTaxon(f8bTaxon)
        local.trips.save(trip)
        local.trips.saveOuting(outing)
    }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun stagesAndTheirEvidenceReopenAndRemovedStageDoesNotEraseDiaryOrSnapshot() = runBlocking<Unit> {
        val departure = trip.destination.copy(name = "Partenza", center = GeoPoint(45.1, 9.1))
        val base = trip.copy(departure = departure)
        val first = TripStage("first", trip.destination, trip.startsOn, testTripRoute())
        val destination = trip.destination.copy(name = "Como", center = GeoPoint(45.2, 9.2))
        val second = TripStage("second", destination, trip.startsOn.plusDays(2), testTripRoute(trip.destination.center, destination.center))
        val planned = base.withStages(listOf(first, second), f8bNow)
        local.trips.save(planned)
        val memory = local.diary.create(observation(outingId = null))
        val snapshot = savedResult().copy(outingId = null, stageId = second.id, area = destination,
            startsOn = second.date, endsOn = second.date, analysisKey = tripAnalysisKey(planned, null, second.id))
        local.trips.saveResult(snapshot)
        db.close(); db = FaunaviaDatabase.open(context, name); local = LocalRepositories(db, clock)
        assertEquals(planned, local.trips.get(trip.id))
        assertEquals(snapshot, local.trips.results(trip.id).single())
        assertEquals(second.route.provenance, local.trips.get(trip.id)!!.stages[1].route.provenance)
        val shorter = planned.withStages(listOf(first), f8bNow)
        local.trips.save(shorter)
        assertEquals(memory, local.diary.get(memory.id))
        assertEquals(snapshot, local.trips.results(trip.id).single())
        assertNotEquals(snapshot.analysisKey, tripAnalysisKey(shorter, null, snapshot.stageId))
        assertTrue(runCatching { local.trips.saveResult(snapshot.copy(id = "invalid")) }.isFailure)
    }

    @Test fun legacyVersionTwoTripWithoutStagesKeepsItsIdentityAndGeometry() = runBlocking<Unit> {
        val planned = trip.copy(departure = trip.destination.copy(center = GeoPoint(45.1, 9.1)), route = testTripRoute())
        local.trips.save(planned)
        val sql = db.openHelper.writableDatabase
        val payload = sql.query("SELECT payload FROM trips WHERE id = 't'").use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
        val legacy = JSONObject(payload).apply { remove("stages"); put("version", 2) }
        sql.execSQL("UPDATE trips SET payload = ? WHERE id = 't'", arrayOf(legacy.toString()))
        assertEquals(planned, local.trips.get(trip.id))
        assertEquals(planned.analysisKey, local.trips.get(trip.id)!!.analysisKey)
    }

    @Test fun chosenTripTraceSurvivesReopeningAndLegacyPayloadStillReads() = runBlocking<Unit> {
        val planned = trip.copy(departure = TripPlace("Partenza", GeoPoint(45.1, 9.1), "CITY", f8bProvenance), route = testTripRoute())
        local.trips.save(planned)
        db.close(); db = FaunaviaDatabase.open(context, name); local = LocalRepositories(db, clock)
        assertEquals(planned, local.trips.get(trip.id))
        val base = context.assets.open("osm-raster-style.json").bufferedReader().use { it.readText() }
        val line = JSONObject(tripRouteMapStyle(base, planned.route!!)).getJSONObject("sources")
            .getJSONObject("trip-route").getJSONObject("data").getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0)
        assertEquals(3, line.length())
        assertEquals(9.02, line.getJSONArray(1).getDouble(0), 0.0)
        val sql = db.openHelper.writableDatabase
        val payload = sql.query("SELECT payload FROM trips WHERE id = 't'").use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
        val legacy = JSONObject(payload).apply { remove("departure"); remove("route"); put("version", 1) }
        sql.execSQL("UPDATE trips SET payload = ? WHERE id = 't'", arrayOf(legacy.toString()))
        assertEquals(trip, local.trips.get(trip.id))
        assertTrue(local.diary.list().isEmpty())
    }

    @Test fun tripsOutingsPlacesResultsAndDraftsSurviveReopeningWithFullProvenance() = runBlocking<Unit> {
        val route = Route("route", "Sentiero", listOf(GeoPoint(45.0, 9.0), GeoPoint(45.01, 9.01)), f8bNow, RouteSource.GPX)
        local.routes.save(route)
        local.trips.saveOuting(outing.copy(route = route))
        local.routes.delete(route.id)
        val place = SavedTripPlace("p", trip.id, trip.destination, f8bNow)
        val result = savedResult()
        local.trips.savePlace(place)
        local.trips.saveResult(result)
        val unidentified = local.unidentified.save(draft())
        db.close()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
        assertEquals(trip, local.trips.get(trip.id))
        assertEquals(route, local.trips.outing(outing.id)!!.route)
        assertEquals(listOf(place), local.trips.places(trip.id))
        assertEquals(listOf(result), local.trips.results(trip.id))
        assertEquals(unidentified, local.unidentified.get("d"))
        assertTrue(local.diary.list().isEmpty())
        local.trips.save(trip.copy(name = "Nome corretto", createdAt = f8bNow.plusSeconds(20), updatedAt = f8bNow.plusSeconds(20)))
        assertEquals(trip.createdAt, local.trips.get(trip.id)!!.createdAt)
        assertEquals("Nome corretto", local.trips.list().single().name)
        local.trips.deletePlace("p"); local.trips.deleteResult("r")
        assertTrue(local.trips.places(trip.id).isEmpty()); assertTrue(local.trips.results(trip.id).isEmpty())
    }

    @Test fun draftConversionRollsBackInvalidTaxonAndThenPreservesOneMemoryAndItsCreationTime() = runBlocking<Unit> {
        val old = local.unidentified.save(draft())
        local.unidentified.save(draft().copy(notes = "Corretto"))
        val invalid = observation("d").copy(taxonId = "missing")
        assertTrue(runCatching { local.unidentified.convert(invalid) }.isFailure)
        assertEquals("Corretto", local.unidentified.get("d")!!.input.notes)
        assertTrue(local.diary.list().isEmpty())
        clock.epochMillis += 10_000
        val converted = local.unidentified.convert(observation("d").copy(notes = "Identificato", location = GeoPoint(45.2, 9.2)))
        assertEquals(old.createdAt, converted.createdAt)
        assertEquals("d", converted.id)
        assertEquals(trip.id, converted.tripId)
        assertEquals(outing.id, converted.outingId)
        assertNull(local.unidentified.get("d"))
        assertEquals(1, local.diary.list().size)
        assertTrue(runCatching { local.unidentified.convert(observation("d")) }.isFailure)
        assertTrue(runCatching { local.unidentified.save(draft()) }.isFailure)
        assertEquals(1, local.diary.list().size)
    }

    @Test fun concurrentDraftConversionCannotDuplicateTheMemory() = runBlocking<Unit> {
        local.unidentified.save(draft())
        val attempts = listOf(async { runCatching { local.unidentified.convert(observation("d")) } },
            async { runCatching { local.unidentified.convert(observation("d")) } }).awaitAll()
        assertEquals(1, attempts.count { it.isSuccess })
        assertNull(local.unidentified.get("d"))
        assertEquals(listOf("d"), local.diary.list().map { it.id })
    }

    @Test fun deletingATripUnlinksMemoriesAndPreservesPhotosEvenThroughRawSql() = runBlocking<Unit> {
        val photo = ObservationPhoto("photo", "o", "photos/p.jpg", "a".repeat(64), 100, "image/jpeg")
        local.diary.create(observation(), listOf(photo))
        local.unidentified.save(draft())
        local.trips.savePlace(SavedTripPlace("p", trip.id, trip.destination, f8bNow))
        local.trips.saveResult(savedResult())
        db.openHelper.writableDatabase.execSQL("DELETE FROM trips WHERE id = 't'")
        val memory = local.diary.get("o")!!
        assertNull(memory.tripId); assertNull(memory.outingId)
        assertNull(local.unidentified.get("d")!!.input.tripId); assertNull(local.unidentified.get("d")!!.input.outingId)
        assertEquals(listOf(photo), local.diary.photos("o"))
        assertTrue(local.trips.outings(trip.id).isEmpty()); assertTrue(local.trips.results(trip.id).isEmpty())
        assertTrue(local.trips.places(trip.id).isEmpty())
        assertEquals(1, local.diary.onDate(memory.localDate, zone).size)
    }

    @Test fun deletingAnOutingPreservesTheTripAndItsMemoriesAndInvalidatesItsSnapshot() = runBlocking<Unit> {
        local.diary.create(observation()); local.unidentified.save(draft()); local.trips.saveResult(savedResult())
        local.trips.deleteOuting(outing.id)
        assertEquals(trip.id, local.diary.get("o")!!.tripId); assertNull(local.diary.get("o")!!.outingId)
        assertEquals(trip.id, local.unidentified.get("d")!!.input.tripId); assertNull(local.unidentified.get("d")!!.input.outingId)
        val result = local.trips.results(trip.id).single()
        assertNull(result.outingId)
        assertNotEquals(tripAnalysisKey(trip, null), result.analysisKey)
        assertNotNull(local.trips.get(trip.id))
    }

    @Test fun databaseRejectsBrokenCrossTripLinksAndDuplicateMemoryIdentities() = runBlocking<Unit> {
        local.trips.save(f8bTrip("other")); local.diary.create(observation()); local.unidentified.save(draft())
        val sql = db.openHelper.writableDatabase
        assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("UPDATE observations SET tripId = 'missing' WHERE id = 'o'") }
        assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("UPDATE observations SET tripId = 'other' WHERE id = 'o'") }
        assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("UPDATE unidentified_drafts SET tripId = NULL WHERE id = 'd'") }
        assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("UPDATE outings SET tripId = 'other' WHERE id = 'out'") }
        assertTrue(runCatching { local.diary.create(observation("d")) }.isFailure)
        assertTrue(runCatching { local.trips.saveResult(savedResult().copy(tripId = "other")) }.isFailure)
        assertTrue(runCatching { local.trips.saveOuting(outing.copy(id = "bad", date = trip.endsOn.plusDays(1))) }.isFailure)
        assertEquals(1, local.diary.list().size); assertEquals(1, local.unidentified.list().size)
    }

    @Test fun diaryChangesRecomputeCalendarSpeciesAndFirstSightingsWithoutCountingDrafts() = runBlocking<Unit> {
        local.unidentified.save(draft())
        local.diary.create(observation("a").copy(observedAt = f8bNow.minusSeconds(86_400)))
        local.diary.create(observation("b"))
        assertEquals("a", PersonalDiaryViews.species(local.diary.list()).single().first.id)
        local.diary.update(observation("a").copy(observedAt = f8bNow.plusSeconds(86_400)))
        assertEquals("b", PersonalDiaryViews.species(local.diary.list()).single().first.id)
        assertEquals(2, PersonalDiaryViews.calendar(local.diary.list()).size)
        local.diary.delete("b")
        assertEquals(1, PersonalDiaryViews.species(local.diary.list()).single().observations)
        assertTrue(PersonalDiaryViews.map(local.diary.list()).isEmpty())
        local.diary.delete("a")
        assertTrue(PersonalDiaryViews.species(local.diary.list()).isEmpty())
        assertEquals(1, local.unidentified.list().size)
        local.unidentified.delete("d"); local.unidentified.delete("missing")
        assertTrue(local.unidentified.list().isEmpty())
    }

    @Test fun selectedResultPreservesItsOriginalAreaPeriodAndSourcesWithoutCreatingASighting() = runBlocking<Unit> {
        val explorer = f8bExplorer(F8BProvider())
        val ready = explorer.explore(explorer.point(trip.destination.name, trip.destination.center), AnalysisPeriod(trip.startsOn, trip.endsOn)) as ExplorationResult.Ready
        val snapshot = resultSnapshot(ready.taxa.single(), trip, null, ready, f8bNow)
        local.trips.saveResult(snapshot)
        val stored = local.trips.results(trip.id).single()
        assertEquals(snapshot, stored)
        assertEquals(trip.startsOn, stored.startsOn); assertEquals(trip.endsOn, stored.endsOn)
        assertEquals(trip.destination, stored.area)
        assertTrue(stored.evidence.any { it.provenance == f8bProvenance })
        assertTrue(local.diary.list().isEmpty())
        assertEquals(tripAnalysisKey(trip, outing), tripAnalysisKey(trip.copy(name = "Nuovo nome"), outing.copy(name = "Nome uscita corretto")))
        assertNotEquals(tripAnalysisKey(trip, outing), tripAnalysisKey(trip, outing.copy(date = outing.date.plusDays(1))))
    }

    @Test fun observationPrefillRequiresAnExactAcceptedIdentityAndDoesNotCreateTheMemory() = runBlocking<Unit> {
        val unavailableSearch = object : TaxonomySearch {
            override suspend fun search(query: String, online: Boolean): TaxonomySearchResult = TaxonomySearchResult.Empty(TaxonomySearchOrigin.OFFLINE_SELECTED)
            override suspend fun select(entry: TaxonomySearchEntry) { fail("No unconfirmed candidate can be selected") }
        }
        val cached = observationPrefill(f8bTaxon.id, f8bTaxon.scientificName, local.catalogue, unavailableSearch, trip.id, outing.id)
        assertEquals(f8bTaxon, cached.taxon); assertEquals(outing.id, cached.outingId)
        val unresolved = observationPrefill("unknown", "Corvus cornix", local.catalogue, unavailableSearch, trip.id)
        assertNull(unresolved.taxon); assertEquals("Corvus cornix", unresolved.query)
        val remoteTaxon = f8bTaxon.copy(id = "remote", scientificName = "Corvus cornix", commonName = null)
        var selections = 0
        val resolver = object : TaxonomySearch {
            override suspend fun search(query: String, online: Boolean) = TaxonomySearchResult.Results(
                listOf(TaxonomySearchEntry(remoteTaxon, emptyList())), TaxonomySearchOrigin.LIVE)
            override suspend fun select(entry: TaxonomySearchEntry) { selections++; local.catalogue.saveTaxon(entry.taxon) }
        }
        assertNull(observationPrefill("missing", "Corvus corax", local.catalogue, resolver).taxon)
        assertEquals(0, selections)
        assertEquals(remoteTaxon, observationPrefill("remote", "Corvus cornix", local.catalogue, resolver).taxon)
        assertEquals(1, selections)
        assertTrue(local.diary.list().isEmpty())
    }

    @Test fun personalMapUsesOnlyLocatedPersonalMemoriesWithoutConnectingSightings() = runBlocking<Unit> {
        val unlocated = local.diary.create(observation("unlocated"))
        val located = local.diary.create(observation("located").copy(location = GeoPoint(45.2, 9.2)))
        val base = context.assets.open("osm-raster-style.json").bufferedReader().use { it.readText() }
        val style = JSONObject(personalMapStyle(base, listOf(unlocated, located)))
        val features = style.getJSONObject("sources").getJSONObject("personal-memories").getJSONObject("data").getJSONArray("features")
        assertEquals(1, features.length())
        assertEquals("Point", features.getJSONObject(0).getJSONObject("geometry").getString("type"))
        assertFalse(style.getJSONObject("sources").has("route"))
    }
}
