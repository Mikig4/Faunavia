package it.faunavia.app

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "f2-persistence.db"
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private val instant = Instant.parse("2026-09-16T22:30:00.123456789Z")
    private val clock = FakeClock(instant.toEpochMilli())
    private val zone = ZoneId.of("Europe/Rome")
    private val source = Provenance("fixture", "record:1", "local replay", instant, "CC0-1.0", "Faunavia synthetic", "test", "v1")
    private val taxon = Taxon("fixture:1", "Turdus merula", "Merlo", "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES", source)
    private fun draft(id: String = "o", taxonId: String = taxon.id, at: Instant = instant) =
        ObservationDraft(id, taxonId, at, zone, GeoPoint(45.5, 9.2), "note: è un test")
    private fun photo(id: String = "p", observation: String = "o") =
        ObservationPhoto(id, observation, "photos/$id.jpg", "a".repeat(64), 100, "image/jpeg")

    @Before fun setup() {
        context.deleteDatabase(name)
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
    }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected invalid write to fail") }
        catch (_: IllegalArgumentException) { /* Domain/reference rejection. */ }
        catch (_: SQLiteConstraintException) { /* SQLite integrity rejection. */ }
    }

    @Test fun emptyDatabaseHasExplicitDefaultsAndMissingReads() = runBlocking {
        assertTrue(local.diary.list().isEmpty())
        assertNull(local.diary.get("missing"))
        assertNull(local.catalogue.taxon("missing"))
        assertNull(local.catalogue.preview("missing"))
        assertNull(local.catalogue.profile("missing"))
        assertNull(local.catalogue.suggestion("missing", "area"))
        assertTrue(local.catalogue.evidence("missing").isEmpty())
        assertTrue(local.routes.list().isEmpty())
        assertEquals(AppSettings(), local.settings.get())
        assertTrue(local.diary.delete("missing").isEmpty())
        assertNull(local.diary.deletePhoto("missing"))
        rejects { local.diary.update(draft()) }
    }

    @Test fun diaryCrudUsesClockAndKeepsPhotosAcrossUpdates() = runBlocking {
        local.catalogue.saveTaxon(taxon)
        val created = local.diary.create(draft(), listOf(photo()))
        assertEquals(Instant.ofEpochMilli(clock.epochMillis), created.createdAt)
        assertEquals(created, local.diary.get("o"))
        clock.epochMillis += 1000
        val changed = local.diary.update(draft().copy(notes = "changed", location = null))
        assertEquals(created.createdAt, changed.createdAt)
        assertEquals(Instant.ofEpochMilli(clock.epochMillis), changed.updatedAt)
        assertNull(changed.location)
        assertEquals(listOf(photo()), local.diary.photos("o"))
        clock.epochMillis -= 10000
        assertEquals(changed.updatedAt, local.diary.update(draft()).updatedAt)
        rejects { local.diary.create(draft()) }
        assertEquals(photo(), local.diary.deletePhoto("p"))
        assertNotNull(local.diary.get("o"))
        local.diary.addPhoto(photo())
        assertEquals(listOf(photo()), local.diary.delete("o"))
        assertNull(local.diary.get("o"))
        assertTrue(local.diary.photos("o").isEmpty())
        assertEquals(taxon, local.catalogue.taxon(taxon.id))
    }

    @Test fun repositoryRejectsMissingNonAnimalAndUnacceptedTaxa() = runBlocking {
        rejects { local.diary.create(draft(taxonId = "")) }
        rejects { local.diary.create(draft(taxonId = "missing")) }
        listOf(taxon.copy(kingdom = "Plantae"), taxon.copy(status = TaxonomicStatus.SYNONYM),
            taxon.copy(status = TaxonomicStatus.DOUBTFUL)).forEach { invalid ->
            local.catalogue.saveTaxon(invalid)
            rejects { local.diary.create(draft()) }
        }
        assertTrue(local.diary.list().isEmpty())
    }

    @Test fun databaseItselfRejectsNullMissingAndInvalidSpeciesOnInsertAndUpdate() = runBlocking {
        local.catalogue.saveTaxon(taxon)
        local.catalogue.saveTaxon(taxon.copy(id = "plant", kingdom = "Plantae"))
        local.catalogue.saveTaxon(taxon.copy(id = "synonym", status = TaxonomicStatus.SYNONYM))
        local.diary.create(draft())
        val sql = db.openHelper.writableDatabase
        for (id in listOf(null, "", "missing", "plant", "synonym")) {
            assertThrows(SQLiteConstraintException::class.java) {
                sql.execSQL("UPDATE observations SET taxonId = ? WHERE id = 'o'", arrayOf<Any?>(id))
            }
            assertThrows(SQLiteConstraintException::class.java) {
                sql.execSQL("INSERT INTO observations SELECT 'bad', ?, observedAt, observedEpochSecond, zoneId, latitude, longitude, notes, createdAt, updatedAt FROM observations WHERE id = 'o'", arrayOf<Any?>(id))
            }
        }
        rejects { local.catalogue.saveTaxon(taxon.copy(kingdom = "Plantae")) }
        rejects { local.catalogue.saveTaxon(taxon.copy(status = TaxonomicStatus.SYNONYM)) }
        rejects { local.catalogue.deleteTaxon(taxon.id) }
        assertEquals(taxon, local.catalogue.taxon(taxon.id))
        assertEquals(taxon.id, local.diary.get("o")!!.taxonId)
    }

    @Test fun failedPhotoInsertRollsBackObservationAndEarlierPhotos() = runBlocking {
        local.catalogue.saveTaxon(taxon)
        rejects { local.diary.create(draft(), listOf(photo(), photo())) }
        assertNull(local.diary.get("o"))
        assertTrue(local.diary.photos("o").isEmpty())
        rejects { local.diary.create(draft(), listOf(photo(observation = "missing"))) }
        assertNull(local.diary.get("o"))
        rejects { local.diary.addPhoto(photo(observation = "missing")) }
        rejects { local.catalogue.savePreview(TaxonPreview("missing", "content:test", source)) }
    }

    @Test fun reopeningPopulatedDatabasePreservesDiaryAndRelations() = runBlocking {
        local.catalogue.saveTaxon(taxon)
        val observation = local.diary.create(draft(), listOf(photo()))
        val settings = AppSettings(true, LocalTime.of(21, 15), zone)
        local.settings.save(settings)
        db.close()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
        assertEquals(observation, local.diary.get("o"))
        assertEquals(listOf(photo()), local.diary.photos("o"))
        assertEquals(taxon, local.catalogue.taxon(taxon.id))
        assertEquals(settings, local.settings.get())
        rejects { local.catalogue.deleteTaxon(taxon.id) }
    }

    @Test fun localDayQueriesRespectMidnightAndBothDstTransitions() = runBlocking {
        local.catalogue.saveTaxon(taxon)
        for (date in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            local.diary.create(draft("$date-before", at = start.minusNanos(1)))
            local.diary.create(draft("$date-start", at = start))
            local.diary.create(draft("$date-last", at = end.minusNanos(1)))
            local.diary.create(draft("$date-after", at = end))
            assertEquals(listOf("$date-start", "$date-last"), local.diary.onDate(date, zone).map { it.id })
        }
        local.diary.create(draft())
        assertEquals(listOf("o"), local.diary.onDate(LocalDate.of(2026, 9, 17), zone).map { it.id })
        assertEquals(listOf("o"), local.diary.onDate(LocalDate.of(2026, 9, 16), ZoneId.of("UTC")).map { it.id })
    }

    @Test fun allMappersAndCatalogueRecordsRoundTripWithoutLosingProvenance() = runBlocking {
        local.catalogue.saveTaxon(taxon)
        val preview = TaxonPreview(taxon.id, "content:licensed-fixture", source)
        val profile = SpeciesProfile(taxon.id, "Descrizione", listOf("bosco", "prati, umidi"), setOf(1, 4, 12), "dieta", null, "comportamento", "note", source)
        val suggestion = SuggestionProfile(taxon.id, "Lombardia", listOf("bosco"), true, 0.25, "Motivo", source)
        val evidence = SourceEvidence("e", taxon.id, EvidenceLevel.DOCUMENTED, instant.minusSeconds(1000), GeoPoint(45.0, 9.0), 100.0, "Historical record", source)
        val route = Route("r", "Percorso", listOf(GeoPoint(45.0, 9.0), GeoPoint(45.1, 9.1)), instant)
        local.catalogue.savePreview(preview)
        local.catalogue.saveProfile(profile)
        local.catalogue.saveSuggestion(suggestion)
        local.catalogue.saveEvidence(evidence)
        local.routes.save(route)
        assertEquals(preview, local.catalogue.preview(taxon.id))
        assertEquals(profile, local.catalogue.profile(taxon.id))
        assertEquals(suggestion, local.catalogue.suggestion(taxon.id, "Lombardia"))
        assertEquals(listOf(evidence), local.catalogue.evidence(taxon.id))
        assertEquals(route, local.routes.get("r"))
        assertEquals(listOf(route), local.routes.list())
        local.routes.save(route.copy(name = "Updated"))
        assertEquals("Updated", local.routes.get("r")!!.name)
        local.routes.delete("r")
        assertNull(local.routes.get("r"))
        local.catalogue.saveEvidence(evidence.copy(level = EvidenceLevel.PLAUSIBLE, eventAt = null, location = null))
        assertEquals(EvidenceLevel.PLAUSIBLE, local.catalogue.evidence(taxon.id).single().level)
        local.catalogue.saveTaxon(taxon.copy(commonName = "Updated"))
        assertEquals(profile, local.catalogue.profile(taxon.id))
        local.catalogue.deleteTaxon(taxon.id)
        assertNull(local.catalogue.preview(taxon.id))
        assertNull(local.catalogue.profile(taxon.id))
        assertNull(local.catalogue.suggestion(taxon.id, "Lombardia"))
        assertTrue(local.catalogue.evidence(taxon.id).isEmpty())
    }
}
