package it.faunavia.exploration

import it.faunavia.domain.AppClock
import it.faunavia.domain.EvidenceLevel
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.occurrence.DocumentedOccurrence
import it.faunavia.occurrence.OccurrenceCacheEntry
import it.faunavia.occurrence.OccurrenceCacheStore
import it.faunavia.occurrence.OccurrenceFailureReason
import it.faunavia.occurrence.OccurrenceGateway
import it.faunavia.occurrence.OccurrenceProvider
import it.faunavia.occurrence.OccurrenceProviderFailure
import it.faunavia.occurrence.OccurrenceProviderId
import it.faunavia.occurrence.OccurrenceProviderResult
import it.faunavia.occurrence.OccurrenceSearchScope
import it.faunavia.occurrence.RetryPolicy
import it.faunavia.plausibility.AnalysisPeriod
import it.faunavia.plausibility.SeasonCompatibility
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationServiceTest {
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val period = AnalysisPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))

    @Test fun gpxGeoJsonAndCoordinatesReachTheSameProviderBoundaryWithoutPersistence() = runBlocking {
        val provider = FakeProvider(listOf(record()))
        val service = service(provider)
        val routes = listOf(
            service.document("test.gpx", """<gpx version="1.1"><rte><rtept lat="45.0" lon="9.0"/></rte></gpx>"""),
            service.document("test.geojson", """{"type":"Point","coordinates":[9.0,45.0]}"""),
            service.point("Milano", GeoPoint(45.0, 9.0)),
        )
        val results = routes.map { service.explore(it, period) as ExplorationResult.Ready }

        assertEquals(1, provider.scopes.size) // The shared geometry fingerprint reuses the F6 cache.
        assertTrue(provider.scopes.all { it.fingerprint.matches(Regex("[a-f0-9]{64}")) && it.queryChunks.isNotEmpty() })
        assertTrue(results.all { it.taxa.single().assessment.level == EvidenceLevel.DOCUMENTED })
        assertEquals(results[0].analysis.fingerprint, results[1].analysis.fingerprint)
        assertEquals(results[1].analysis.fingerprint, results[2].analysis.fingerprint)
    }

    @Test fun seasonChangesExplanationButDoesNotInventPlausibility() = runBlocking {
        val service = service(FakeProvider(listOf(record())))
        val route = service.point("Milano", GeoPoint(45.0, 9.0))
        val april = service.explore(route, period) as ExplorationResult.Ready
        val december = service.explore(route, AnalysisPeriod(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-10"))) as ExplorationResult.Ready

        assertEquals(EvidenceLevel.DOCUMENTED, april.taxa.single().assessment.level)
        assertEquals(EvidenceLevel.DOCUMENTED, december.taxa.single().assessment.level)
        assertEquals(SeasonCompatibility.SUPPORTS, april.taxa.single().assessment.season.compatibility)
        assertEquals(SeasonCompatibility.UNKNOWN, december.taxa.single().assessment.season.compatibility)
        assertTrue(april.taxa.single().assessment.provenance.isNotEmpty())
    }

    @Test fun oldOrImpreciseEvidenceNeverBecomesDocumentedOrPlausible() = runBlocking {
        val service = service(FakeProvider(listOf(record().copy(observedOn = "2010-04-12"),
            record().copy(id = "gbif:2", providerRecordId = "2", coordinateUncertaintyMeters = 50_000.0))))
        val result = service.explore(service.point("Milano", GeoPoint(45.0, 9.0)), period) as ExplorationResult.Ready
        assertEquals(EvidenceLevel.INSUFFICIENT, result.taxa.single().assessment.level)
        assertTrue(result.taxa.single().assessment.explanation.any { it.conclusion == "no-usable-direct-occurrence" })
    }

    @Test fun emptyAndUnavailableAreDifferentStates() = runBlocking {
        val route = service(FakeProvider(emptyList())).point("Milano", GeoPoint(45.0, 9.0))
        val empty = service(FakeProvider(emptyList())).explore(route, period) as ExplorationResult.Ready
        val unavailable = service(FakeProvider(null)).explore(route, period)
        assertTrue(empty.taxa.isEmpty())
        assertFalse(empty.partial)
        assertTrue(unavailable is ExplorationResult.Unavailable)
    }

    @Test fun genusOnlyEvidenceIsCountedWithoutInventingASpeciesOrCallingTheAreaEmpty() = runBlocking {
        val genus = record().copy(scientificName = "Turdus", taxonId = null)
        val service = service(FakeProvider(listOf(genus)))
        val result = service.explore(service.point("Milano", GeoPoint(45.0, 9.0)), period) as ExplorationResult.Ready
        assertTrue(result.taxa.isEmpty())
        assertEquals(1, result.unclassifiedCount)
    }

    private fun service(provider: FakeProvider): ExplorationService = ExplorationService(
        gateway = OccurrenceGateway(MemoryCache(), listOf(provider), object : AppClock {
            override fun nowEpochMillis(): Long = now.toEpochMilli()
        },
            retryPolicy = RetryPolicy(maxAttempts = 1)),
        now = { now },
    )

    private fun record(): DocumentedOccurrence = DocumentedOccurrence(
        id = "gbif:1", provider = OccurrenceProviderId.GBIF, providerRecordId = "1",
        taxonId = "gbif:2492481", scientificName = "Turdus merula", observedOn = "2026-04-12",
        location = GeoPoint(45.0, 9.0), coordinateUncertaintyMeters = 100.0,
        sourceUrl = "https://example.test/1",
        provenance = Provenance("GBIF", "1", "fixture", now, "CC BY", "Fixture dataset", "fixture", "v1"),
    )

    private class FakeProvider(private val records: List<DocumentedOccurrence>?) : OccurrenceProvider {
        override val id = OccurrenceProviderId.GBIF
        val scopes = mutableListOf<OccurrenceSearchScope>()
        override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult {
            scopes += scope
            return records?.let { OccurrenceProviderResult.Success(it) }
                ?: OccurrenceProviderResult.Failure(OccurrenceProviderFailure(id, OccurrenceFailureReason.NETWORK, "offline"))
        }
    }

    private class MemoryCache : OccurrenceCacheStore {
        private val entries = mutableMapOf<String, OccurrenceCacheEntry>()
        override suspend fun read(key: String): OccurrenceCacheEntry? = entries[key]
        override suspend fun save(entry: OccurrenceCacheEntry) { entries[entry.key] = entry }
        override suspend fun delete(key: String) { entries.remove(key) }
        override suspend fun clear() { entries.clear() }
    }
}
