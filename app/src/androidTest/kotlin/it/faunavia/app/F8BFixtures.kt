package it.faunavia.app

import it.faunavia.domain.*
import it.faunavia.exploration.ExplorationService
import it.faunavia.occurrence.*
import it.faunavia.taxonomy.*
import it.faunavia.testing.FakeClock
import java.time.Instant
import java.time.LocalDate

internal val f8bNow: Instant = Instant.parse("2026-09-30T10:00:00.123Z")
internal val f8bProvenance = Provenance("fixture", "2490719", "test", f8bNow, "CC0", "Faunavia tests", "synthetic", "v1")
internal val f8bTaxon = Taxon("gbif:2490719", "Turdus merula", "Merlo", "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES", f8bProvenance)
internal fun f8bTrip(id: String = "t", name: String = "Lombardia") = Trip(id, name,
    TripPlace("Milano", GeoPoint(45.0, 9.0), "CITY", f8bProvenance),
    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 1_000.0, setOf(AnimalInterest.BIRDS), f8bNow, f8bNow)

internal fun testTripRoute(start: GeoPoint = GeoPoint(45.1, 9.1), end: GeoPoint = GeoPoint(45.0, 9.0)) =
    TripRoute(Route("planned-fixture", "Percorso scelto", listOf(start, GeoPoint(45.04, 9.02), end),
        f8bNow, RouteSource.PLANNED), 15_000.0, 1_200.0, f8bProvenance)

internal class F8BOfflineTaxonomy : TaxonomySearch {
    override suspend fun search(query: String, online: Boolean): TaxonomySearchResult =
        if (listOf(f8bTaxon.scientificName, f8bTaxon.commonName!!).any { it.contains(query, true) })
            TaxonomySearchResult.Results(listOf(TaxonomySearchEntry(f8bTaxon, listOf("Merlo"))), TaxonomySearchOrigin.OFFLINE_SELECTED)
        else TaxonomySearchResult.Empty(TaxonomySearchOrigin.OFFLINE_SELECTED)
    override suspend fun select(entry: TaxonomySearchEntry) = Unit
}

internal class F8BProvider : OccurrenceProvider {
    override val id = OccurrenceProviderId.GBIF
    @Volatile var calls = 0
    @Volatile var unavailable = false
    override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult {
        calls++
        return if (unavailable) OccurrenceProviderResult.Failure(OccurrenceProviderFailure(id, OccurrenceFailureReason.MALFORMED_RESPONSE, "offline fixture"))
        else OccurrenceProviderResult.Success(listOf(DocumentedOccurrence("gbif:record", id, "record", f8bTaxon.id,
            f8bTaxon.scientificName, "2026-09-01", GeoPoint(45.0, 9.0), 20.0, "https://example.test/record", f8bProvenance)))
    }
}

internal fun f8bExplorer(provider: F8BProvider) = ExplorationService(OccurrenceGateway(object : OccurrenceCacheStore {
    private val entries = mutableMapOf<String, OccurrenceCacheEntry>()
    override suspend fun read(key: String) = entries[key]
    override suspend fun save(entry: OccurrenceCacheEntry) { entries[entry.key] = entry }
    override suspend fun delete(key: String) { entries.remove(key) }
    override suspend fun clear() { entries.clear() }
}, listOf(provider), FakeClock(f8bNow.toEpochMilli())), now = { f8bNow })
