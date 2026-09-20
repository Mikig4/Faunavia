package it.faunavia.taxonomy

import it.faunavia.domain.AppClock
import it.faunavia.domain.CatalogueRepository
import it.faunavia.domain.Taxon

const val MINIMUM_TAXON_QUERY_LENGTH = 2

enum class TaxonomyFailure {
    TIMEOUT,
    MALFORMED_RESPONSE,
    NETWORK,
}

data class TaxonomyCandidate(
    val taxon: Taxon,
    val aliases: List<String> = emptyList(),
)

sealed interface TaxonomyProviderResult {
    data class Success(val candidates: List<TaxonomyCandidate>) : TaxonomyProviderResult
    data object Empty : TaxonomyProviderResult
    data class Failure(val reason: TaxonomyFailure) : TaxonomyProviderResult
}

/** Remote taxonomy adapter boundary. UI and Room do not know provider request shapes. */
interface TaxonomyProvider {
    suspend fun suggest(query: String, limit: Int): TaxonomyProviderResult
}

data class StoredTaxon(
    val taxon: Taxon,
    val aliases: List<String>,
)

/** The small local cache contains only taxa explicitly selected by the person using the app. */
interface TaxonSelectionStore {
    suspend fun searchSelected(query: String, limit: Int): List<StoredTaxon>
    suspend fun saveSelected(taxon: Taxon, aliases: List<String>)
}

class CatalogueTaxonSelectionStore(
    private val catalogue: CatalogueRepository,
) : TaxonSelectionStore {
    override suspend fun searchSelected(query: String, limit: Int): List<StoredTaxon> =
        catalogue.searchTaxa(query, limit).map { taxon ->
            StoredTaxon(taxon, catalogue.aliases(taxon.id).map { it.name })
        }

    override suspend fun saveSelected(taxon: Taxon, aliases: List<String>) {
        catalogue.saveTaxonWithAliases(taxon, aliases)
    }
}

data class TaxonomySearchEntry(
    val taxon: Taxon,
    val aliases: List<String>,
) {
    init { require(taxon.isSelectable) { "A catalogue entry must be an accepted Animalia taxon." } }

    /** F3 does not copy provider media; the UI renders a reusable explicit placeholder. */
    val previewAvailable: Boolean get() = false
}

enum class TaxonomySearchOrigin {
    LIVE,
    MEMORY_CACHE,
    OFFLINE_SELECTED,
}

sealed interface TaxonomySearchResult {
    data class AwaitingQuery(val minimumLength: Int = MINIMUM_TAXON_QUERY_LENGTH) : TaxonomySearchResult
    data class Results(
        val entries: List<TaxonomySearchEntry>,
        val origin: TaxonomySearchOrigin,
    ) : TaxonomySearchResult
    data class Empty(val origin: TaxonomySearchOrigin) : TaxonomySearchResult
    data class Failure(
        val reason: TaxonomyFailure,
        val cachedEntries: List<TaxonomySearchEntry>,
    ) : TaxonomySearchResult
}

interface TaxonomySearch {
    suspend fun search(query: String, online: Boolean = true): TaxonomySearchResult
    suspend fun select(entry: TaxonomySearchEntry)
}

/**
 * Merges the current provider autocomplete with selected taxa stored in Room.
 * Provider suggestions expire in memory; selected taxa remain readable without a connection.
 */
class TaxonomySearchService(
    private val store: TaxonSelectionStore,
    private val provider: TaxonomyProvider,
    private val clock: AppClock,
    private val memoryCacheTtlMillis: Long = 5 * 60 * 1000L,
    private val resultLimit: Int = 8,
) : TaxonomySearch {
    private data class CachedSuggestions(
        val entries: List<TaxonomySearchEntry>,
        val expiresAtMillis: Long,
    )

    private val memory = mutableMapOf<String, CachedSuggestions>()

    init {
        require(memoryCacheTtlMillis > 0) { "Taxonomy cache TTL must be positive." }
        require(resultLimit > 0) { "Taxonomy result limit must be positive." }
    }

    override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
        val normalizedQuery = normalizeQuery(query)
        if (normalizedQuery.length < MINIMUM_TAXON_QUERY_LENGTH) {
            return TaxonomySearchResult.AwaitingQuery()
        }

        val selected = entriesFor(store.searchSelected(normalizedQuery, resultLimit).map {
            TaxonomyCandidate(it.taxon, it.aliases)
        })
        if (!online) {
            return resultsOrEmpty(selected, TaxonomySearchOrigin.OFFLINE_SELECTED)
        }

        val now = clock.nowEpochMillis()
        memory[normalizedQuery]?.takeIf { now < it.expiresAtMillis }?.let { cached ->
            return resultsOrEmpty(merge(selected, cached.entries), TaxonomySearchOrigin.MEMORY_CACHE)
        }

        return when (val remote = provider.suggest(normalizedQuery, resultLimit)) {
            TaxonomyProviderResult.Empty -> resultsOrEmpty(selected, TaxonomySearchOrigin.LIVE)
            is TaxonomyProviderResult.Failure -> TaxonomySearchResult.Failure(remote.reason, selected)
            is TaxonomyProviderResult.Success -> {
                val remoteEntries = entriesFor(remote.candidates)
                memory[normalizedQuery] = CachedSuggestions(remoteEntries, now + memoryCacheTtlMillis)
                resultsOrEmpty(merge(selected, remoteEntries), TaxonomySearchOrigin.LIVE)
            }
        }
    }

    override suspend fun select(entry: TaxonomySearchEntry) {
        require(entry.taxon.isSelectable) { "Only accepted Animalia taxa can be selected." }
        store.saveSelected(entry.taxon, entry.aliases)
    }

    private fun resultsOrEmpty(
        entries: List<TaxonomySearchEntry>,
        origin: TaxonomySearchOrigin,
    ): TaxonomySearchResult = if (entries.isEmpty()) TaxonomySearchResult.Empty(origin)
    else TaxonomySearchResult.Results(entries, origin)

    private fun merge(
        first: List<TaxonomySearchEntry>,
        second: List<TaxonomySearchEntry>,
    ): List<TaxonomySearchEntry> = entriesFor((first + second).map {
        TaxonomyCandidate(it.taxon, it.aliases)
    })

    private fun entriesFor(candidates: List<TaxonomyCandidate>): List<TaxonomySearchEntry> {
        val byId = linkedMapOf<String, TaxonomyCandidate>()
        candidates.filter { it.taxon.isSelectable }.forEach { candidate ->
            val existing = byId[candidate.taxon.id]
            byId[candidate.taxon.id] = if (existing == null) candidate else TaxonomyCandidate(
                taxon = existing.taxon,
                aliases = existing.aliases + candidate.aliases,
            )
        }
        return byId.values.map { candidate ->
            TaxonomySearchEntry(
                taxon = candidate.taxon,
                aliases = (candidate.aliases + candidate.taxon.scientificName + listOfNotNull(candidate.taxon.commonName))
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinctBy(::normalizeQuery),
            )
        }.sortedWith(compareBy(::rankOrder, { it.taxon.scientificName.lowercase() }, { it.taxon.id })).take(resultLimit)
    }

    private fun rankOrder(entry: TaxonomySearchEntry): Int = when (entry.taxon.rank.uppercase()) {
        "SPECIES" -> 0
        "SUBSPECIES" -> 1
        else -> 2
    }
}

fun normalizeQuery(query: String): String = query.trim().lowercase().replace(Regex("\\s+"), " ")
