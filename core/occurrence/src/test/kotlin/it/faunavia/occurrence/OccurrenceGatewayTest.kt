package it.faunavia.occurrence

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.route.CorridorPortion
import it.faunavia.route.GeoBounds
import it.faunavia.route.MetricCell
import it.faunavia.route.QueryChunk
import it.faunavia.route.RouteAnalysis
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceGatewayTest {
    private val clock = MutableClock(1_758_196_800_000L)

    @Test
    fun freshCacheSkipsProviderAndStaleCacheIsExplicitAfterFailure() = runBlocking<Unit> {
        val cache = MemoryCache()
        val provider = ScriptedProvider(
            OccurrenceProviderId.GBIF,
            mutableListOf(OccurrenceProviderResult.Success(listOf(record("one")))),
        )
        val gateway = OccurrenceGateway(cache, listOf(provider), clock, ttlMillis = 1_000L)

        assertEquals(OccurrenceResultOrigin.NETWORK, (gateway.search(analysis()) as OccurrenceSearchResult.Records).origin)
        assertEquals(1, provider.calls)
        assertEquals(OccurrenceResultOrigin.FRESH_CACHE, (gateway.search(analysis()) as OccurrenceSearchResult.Records).origin)
        assertEquals(1, provider.calls)

        clock.now += 1_000L
        provider.responses += OccurrenceProviderResult.Failure(
            OccurrenceProviderFailure(OccurrenceProviderId.GBIF, OccurrenceFailureReason.MALFORMED_RESPONSE, "bad fixture"),
        )
        val stale = gateway.search(analysis()) as OccurrenceSearchResult.Records

        assertEquals(OccurrenceResultOrigin.STALE_CACHE, stale.origin)
        assertEquals(listOf("gbif:one"), stale.occurrences.map(DocumentedOccurrence::id))
        assertEquals(1, stale.providerFailures.size)
    }

    @Test
    fun retriesAreBoundedAndRateLimitedResponseCanRecover() = runBlocking<Unit> {
        val provider = ScriptedProvider(
            OccurrenceProviderId.GBIF,
            mutableListOf(
                OccurrenceProviderResult.Failure(
                    OccurrenceProviderFailure(OccurrenceProviderId.GBIF, OccurrenceFailureReason.RATE_LIMITED, "slow down"),
                ),
                OccurrenceProviderResult.Success(listOf(record("recovered"))),
            ),
        )
        val waits = mutableListOf<Long>()
        val result = OccurrenceGateway(
            MemoryCache(),
            listOf(provider),
            clock,
            retryPolicy = RetryPolicy(maxAttempts = 3, initialBackoffMillis = 25L),
            retryDelay = RetryDelay { waits += it },
        ).search(analysis()) as OccurrenceSearchResult.Records

        assertEquals(OccurrenceResultOrigin.NETWORK, result.origin)
        assertEquals(listOf(25L), waits)
        assertEquals(2, provider.calls)
    }

    @Test
    fun duplicatesFromOverlappingProviderAreasAreRemovedByProviderRecordIdentity() = runBlocking<Unit> {
        val duplicate = record("same")
        val otherProvider = record("same").copy(
            id = "nnb:same",
            provider = OccurrenceProviderId.NNB,
            provenance = record("same").provenance.copy(source = "NNB", recordId = "same"),
        )
        val provider = ScriptedProvider(
            OccurrenceProviderId.GBIF,
            mutableListOf(OccurrenceProviderResult.Success(listOf(duplicate, duplicate, otherProvider))),
        )

        val records = OccurrenceGateway(MemoryCache(), listOf(provider), clock).search(analysis()) as OccurrenceSearchResult.Records

        assertEquals(listOf("gbif:same", "nnb:same"), records.occurrences.map(DocumentedOccurrence::id))
    }

    private fun analysis(): RouteAnalysis = RouteAnalysis(
        routeId = "route-fixture",
        routeName = "Fixture",
        projection = "EPSG:3035",
        totalLengthMeters = 10.0,
        estimatedCorridorAreaSquareMeters = 1_000.0,
        bounds = GeoBounds(9.0, 45.0, 9.1, 45.1),
        samples = listOf(GeoPoint(45.0, 9.0)),
        corridorPortions = listOf(
            CorridorPortion(
                "corridor/0/0",
                0,
                listOf(GeoPoint(45.0, 9.0), GeoPoint(45.0, 9.1), GeoPoint(45.1, 9.1), GeoPoint(45.1, 9.0), GeoPoint(45.0, 9.0)),
                GeoBounds(9.0, 45.0, 9.1, 45.1),
            ),
        ),
        cells = listOf(MetricCell("cell", 0, 0)),
        queryChunks = listOf(QueryChunk("chunk/0/0", listOf("cell"), GeoBounds(9.0, 45.0, 9.1, 45.1))),
        fingerprint = "a".repeat(64),
        warnings = emptyList(),
    )

    private fun record(id: String): DocumentedOccurrence = DocumentedOccurrence(
        id = "gbif:$id",
        provider = OccurrenceProviderId.GBIF,
        providerRecordId = id,
        taxonId = "gbif:1",
        scientificName = "Turdus merula",
        observedOn = "2026-01-01",
        location = GeoPoint(45.0, 9.0),
        coordinateUncertaintyMeters = 800.0,
        sourceUrl = "https://example.test/$id",
        provenance = Provenance("GBIF", id, "fixture", Instant.ofEpochMilli(clock.now), "CC BY", "Dataset", "fixture", "v1"),
    )

    private class MutableClock(var now: Long) : AppClock {
        override fun nowEpochMillis(): Long = now
    }

    private class MemoryCache : OccurrenceCacheStore {
        private val entries = mutableMapOf<String, OccurrenceCacheEntry>()
        override suspend fun read(key: String): OccurrenceCacheEntry? = entries[key]
        override suspend fun save(entry: OccurrenceCacheEntry) { entries[entry.key] = entry }
        override suspend fun delete(key: String) { entries.remove(key) }
        override suspend fun clear() { entries.clear() }
    }

    private class ScriptedProvider(
        override val id: OccurrenceProviderId,
        val responses: MutableList<OccurrenceProviderResult>,
    ) : OccurrenceProvider {
        var calls: Int = 0
        override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult {
            assertTrue(scope.fingerprint.isNotBlank())
            calls++
            return responses.removeAt(0)
        }
    }
}
