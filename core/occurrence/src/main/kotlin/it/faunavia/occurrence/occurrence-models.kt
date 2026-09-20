package it.faunavia.occurrence

import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.route.CorridorPortion
import it.faunavia.route.QueryChunk
import it.faunavia.route.RouteAnalysis
import java.time.Instant

/** Providers are identifiers, never UI-facing names or network hostnames. */
enum class OccurrenceProviderId { GBIF, NNB }

/**
 * A source occurrence. It is documented evidence, not a claim that an animal is present now.
 * Coordinates are retained exactly as the provider supplied them; no location is reconstructed.
 */
data class DocumentedOccurrence(
    val id: String,
    val provider: OccurrenceProviderId,
    val providerRecordId: String,
    val taxonId: String?,
    val scientificName: String,
    val observedOn: String?,
    val location: GeoPoint?,
    val coordinateUncertaintyMeters: Double?,
    val sourceUrl: String,
    val provenance: Provenance,
) {
    init {
        require(id.isNotBlank() && providerRecordId.isNotBlank() && scientificName.isNotBlank())
        require(sourceUrl.isNotBlank())
        require(coordinateUncertaintyMeters == null ||
            (coordinateUncertaintyMeters.isFinite() && coordinateUncertaintyMeters >= 0.0))
        require(observedOn == null || observedOn.isNotBlank())
    }
}

/** F5 geometry is converted into this provider-neutral, bounded query scope. */
data class OccurrenceSearchScope(
    val routeId: String,
    val fingerprint: String,
    val corridorPortions: List<CorridorPortion>,
    val queryChunks: List<QueryChunk>,
) {
    init {
        require(routeId.isNotBlank() && fingerprint.matches(Regex("[a-f0-9]{64}")))
        require(corridorPortions.isNotEmpty() && queryChunks.isNotEmpty())
    }

    companion object {
        fun from(analysis: RouteAnalysis): OccurrenceSearchScope = OccurrenceSearchScope(
            routeId = analysis.routeId,
            fingerprint = analysis.fingerprint,
            corridorPortions = analysis.corridorPortions,
            queryChunks = analysis.queryChunks,
        )
    }
}

enum class OccurrenceFailureReason(
    val retryable: Boolean,
) {
    TIMEOUT(true),
    NETWORK(true),
    RATE_LIMITED(true),
    SERVICE_UNAVAILABLE(true),
    MALFORMED_RESPONSE(false),
    UNEXPECTED_RESPONSE(false),
}

data class OccurrenceProviderFailure(
    val provider: OccurrenceProviderId,
    val reason: OccurrenceFailureReason,
    val detail: String,
    val retryAfterMillis: Long? = null,
) {
    init {
        require(detail.isNotBlank())
        require(retryAfterMillis == null || retryAfterMillis >= 0L)
    }
}

sealed interface OccurrenceProviderResult {
    data class Success(val occurrences: List<DocumentedOccurrence>) : OccurrenceProviderResult
    data class Failure(val failure: OccurrenceProviderFailure) : OccurrenceProviderResult
}

/** An adapter decides whether each spatial request uses a polygon or a bounding box. */
interface OccurrenceProvider {
    val id: OccurrenceProviderId
    suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult
}

data class OccurrenceCacheEntry(
    val key: String,
    val cachedAt: Instant,
    val expiresAt: Instant,
    val occurrences: List<DocumentedOccurrence>,
) {
    init {
        require(key.isNotBlank())
        require(!expiresAt.isBefore(cachedAt))
    }
}

/** Persistent local cache boundary; implementations must make deletion deliberate and explicit. */
interface OccurrenceCacheStore {
    suspend fun read(key: String): OccurrenceCacheEntry?
    suspend fun save(entry: OccurrenceCacheEntry)
    suspend fun delete(key: String)
    suspend fun clear()
}

enum class OccurrenceResultOrigin { NETWORK, FRESH_CACHE, STALE_CACHE }

sealed interface OccurrenceSearchResult {
    data class Records(
        val occurrences: List<DocumentedOccurrence>,
        val origin: OccurrenceResultOrigin,
        val providerFailures: List<OccurrenceProviderFailure> = emptyList(),
    ) : OccurrenceSearchResult

    data class Failure(
        val providerFailures: List<OccurrenceProviderFailure>,
    ) : OccurrenceSearchResult {
        init { require(providerFailures.isNotEmpty()) }
    }
}
