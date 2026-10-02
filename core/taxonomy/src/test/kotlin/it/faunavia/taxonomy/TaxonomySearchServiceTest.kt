package it.faunavia.taxonomy

import it.faunavia.domain.AppClock
import it.faunavia.domain.Provenance
import it.faunavia.domain.Taxon
import it.faunavia.domain.TaxonomicStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxonomySearchServiceTest {
    private val clock = MutableClock(Instant.parse("2026-09-18T10:00:00Z").toEpochMilli())
    private val source = Provenance(
        "fixture", "record", "taxonomy test", Instant.ofEpochMilli(clock.nowEpochMillis()),
        "CC0", "Faunavia test", "test", "v1",
    )

    @Test fun shortQueriesDoNotCallTheProvider() = runBlocking {
        val provider = FakeProvider()
        val result = service(provider).search("m")
        assertEquals(TaxonomySearchResult.AwaitingQuery(), result)
        assertTrue(provider.queries.isEmpty())
    }

    @Test fun filtersAnimaliaDeduplicatesAndPrefersSpecies() = runBlocking {
        val accepted = animal("gbif:1", "Turdus merula", "SPECIES")
        val provider = FakeProvider(TaxonomyProviderResult.Success(listOf(
            TaxonomyCandidate(accepted, listOf("Merlo")),
            TaxonomyCandidate(accepted, listOf("European blackbird")),
            TaxonomyCandidate(accepted.copy(id = "gbif:2", rank = "GENUS"), listOf("Tordi")),
            TaxonomyCandidate(accepted.copy(id = "gbif:3", kingdom = "Plantae"), listOf("pianta")),
            TaxonomyCandidate(accepted.copy(id = "gbif:4", status = TaxonomicStatus.SYNONYM), listOf("sinonimo")),
        )))

        val result = service(provider).search("merlo") as TaxonomySearchResult.Results

        assertEquals(TaxonomySearchOrigin.LIVE, result.origin)
        assertEquals(listOf("gbif:1", "gbif:2"), result.entries.map { it.taxon.id })
        assertTrue(result.entries.first().aliases.containsAll(listOf("Merlo", "European blackbird")))
    }

    @Test fun selectedTaxonAndItsSynonymAreAvailableOfflineWithoutPreview() = runBlocking {
        val provider = FakeProvider(TaxonomyProviderResult.Failure(TaxonomyFailure.NETWORK))
        val catalogue = InMemorySelectionStore()
        val search = TaxonomySearchService(catalogue, provider, clock)
        val entry = TaxonomySearchEntry(animal("gbif:1", "Turdus merula"), listOf("Merlo", "Merula nigra"))

        assertTrue(!entry.previewAvailable)
        search.select(entry)
        val result = search.search("nigra", online = false) as TaxonomySearchResult.Results

        assertEquals(TaxonomySearchOrigin.OFFLINE_SELECTED, result.origin)
        assertEquals(listOf("gbif:1"), result.entries.map { it.taxon.id })
        assertTrue(provider.queries.isEmpty())
    }

    @Test fun memoryCacheHasControlledExpiryBeforeRefreshing() = runBlocking {
        val provider = FakeProvider(TaxonomyProviderResult.Success(listOf(TaxonomyCandidate(animal("gbif:1", "Turdus merula")))))
        val search = service(provider, cacheTtlMillis = 100)

        assertEquals(TaxonomySearchOrigin.LIVE, (search.search("merlo") as TaxonomySearchResult.Results).origin)
        clock.epochMillis += 99
        assertEquals(TaxonomySearchOrigin.MEMORY_CACHE, (search.search("merlo") as TaxonomySearchResult.Results).origin)
        clock.epochMillis += 1
        assertEquals(TaxonomySearchOrigin.LIVE, (search.search("merlo") as TaxonomySearchResult.Results).origin)
        assertEquals(listOf("merlo", "merlo"), provider.queries)
    }

    @Test fun exposesEmptyAndFailureWithSelectedFallback() = runBlocking {
        val emptyProvider = FakeProvider(TaxonomyProviderResult.Empty)
        assertEquals(TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE), service(emptyProvider).search("merlo"))

        val store = InMemorySelectionStore()
        store.saveSelected(animal("gbif:1", "Turdus merula"), listOf("Merlo"))
        val failure = TaxonomySearchService(store, FakeProvider(TaxonomyProviderResult.Failure(TaxonomyFailure.TIMEOUT)), clock)
            .search("merlo") as TaxonomySearchResult.Failure
        assertEquals(TaxonomyFailure.TIMEOUT, failure.reason)
        assertEquals(listOf("gbif:1"), failure.cachedEntries.map { it.taxon.id })
    }

    @Test fun exactCommonNameIsShownBeforeAlphabeticalPartialMatches() = runBlocking {
        val partial = animal("gbif:2", "Aves fixture").copy(commonName = "Merlo dal collare")
        val exact = animal("gbif:1", "Turdus merula")
        val foreign = animal("gbif:3", "Labrus merula").copy(commonName = "Labride verde")
        val result = service(FakeProvider(TaxonomyProviderResult.Success(listOf(
            TaxonomyCandidate(partial, listOf("Merlo dal collare")), TaxonomyCandidate(exact, listOf("Merlo")),
            TaxonomyCandidate(foreign, listOf("Merlo")),
        )))).search("merlo") as TaxonomySearchResult.Results
        assertEquals(listOf("gbif:1", "gbif:3", "gbif:2"), result.entries.map { it.taxon.id })
    }

    private fun service(
        provider: TaxonomyProvider,
        cacheTtlMillis: Long = 5 * 60 * 1000L,
    ): TaxonomySearchService = TaxonomySearchService(InMemorySelectionStore(), provider, clock, cacheTtlMillis)

    private fun animal(id: String, name: String, rank: String = "SPECIES"): Taxon = Taxon(
        id, name, "Merlo", "Animalia", TaxonomicStatus.ACCEPTED, rank, source,
    )

    private class MutableClock(var epochMillis: Long) : AppClock {
        override fun nowEpochMillis(): Long = epochMillis
    }

    private class FakeProvider(
        private var result: TaxonomyProviderResult = TaxonomyProviderResult.Empty,
    ) : TaxonomyProvider {
        val queries = mutableListOf<String>()
        override suspend fun suggest(query: String, limit: Int): TaxonomyProviderResult {
            queries += query
            return result
        }
    }

    private class InMemorySelectionStore : TaxonSelectionStore {
        private val selected = linkedMapOf<String, StoredTaxon>()
        override suspend fun searchSelected(query: String, limit: Int): List<StoredTaxon> = selected.values.filter { stored ->
            (stored.aliases + stored.taxon.scientificName + listOfNotNull(stored.taxon.commonName))
                .any { normalizeQuery(it).contains(query) }
        }.take(limit)

        override suspend fun saveSelected(taxon: Taxon, aliases: List<String>) {
            selected[taxon.id] = StoredTaxon(taxon, aliases)
        }
    }
}
