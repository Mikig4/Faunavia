package it.faunavia.occurrence

import it.faunavia.domain.AppClock
import it.faunavia.route.RouteAnalysis
import java.time.Instant
import kotlinx.coroutines.delay

fun interface RetryDelay {
    suspend fun waitMillis(millis: Long)
}

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoffMillis: Long = 250L,
) {
    init {
        require(maxAttempts in 1..5)
        require(initialBackoffMillis > 0L)
    }
}

/**
 * Coordinates cache, retries and cross-portion de-duplication without exposing provider details.
 * A stale cache is returned explicitly when every current request fails.
 */
class OccurrenceGateway(
    private val cache: OccurrenceCacheStore,
    private val providers: List<OccurrenceProvider>,
    private val clock: AppClock,
    private val ttlMillis: Long = 6 * 60 * 60 * 1_000L,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val retryDelay: RetryDelay = RetryDelay { delay(it) },
) {
    init {
        require(providers.isNotEmpty()) { "At least one occurrence provider is required." }
        require(providers.map(OccurrenceProvider::id).distinct().size == providers.size) {
            "Occurrence providers must have distinct IDs."
        }
        require(ttlMillis > 0L)
    }

    suspend fun search(
        analysis: RouteAnalysis,
        refresh: Boolean = false,
    ): OccurrenceSearchResult {
        val key = cacheKey(analysis)
        val now = Instant.ofEpochMilli(clock.nowEpochMillis())
        val cached = cache.read(key)
        if (!refresh && cached != null && now.isBefore(cached.expiresAt)) {
            return OccurrenceSearchResult.Records(cached.occurrences, OccurrenceResultOrigin.FRESH_CACHE)
        }

        val scope = OccurrenceSearchScope.from(analysis)
        val successes = mutableListOf<DocumentedOccurrence>()
        val failures = mutableListOf<OccurrenceProviderFailure>()
        providers.forEach { provider ->
            when (val result = searchWithRetry(provider, scope)) {
                is OccurrenceProviderResult.Success -> successes += result.occurrences
                is OccurrenceProviderResult.Failure -> failures += result.failure
            }
        }
        if (failures.isNotEmpty() && successes.isEmpty()) {
            return cached?.let {
                OccurrenceSearchResult.Records(it.occurrences, OccurrenceResultOrigin.STALE_CACHE, failures)
            } ?: OccurrenceSearchResult.Failure(failures)
        }

        val records = deduplicate(successes)
        if (failures.isEmpty()) {
            val cachedAt = Instant.ofEpochMilli(clock.nowEpochMillis())
            cache.save(OccurrenceCacheEntry(key, cachedAt, cachedAt.plusMillis(ttlMillis), records))
        }
        return OccurrenceSearchResult.Records(records, OccurrenceResultOrigin.NETWORK, failures)
    }

    private suspend fun searchWithRetry(
        provider: OccurrenceProvider,
        scope: OccurrenceSearchScope,
    ): OccurrenceProviderResult {
        var attempt = 1
        while (true) {
            when (val result = provider.search(scope)) {
                is OccurrenceProviderResult.Success -> return result
                is OccurrenceProviderResult.Failure -> {
                    if (!result.failure.reason.retryable || attempt >= retryPolicy.maxAttempts) return result
                    val exponential = retryPolicy.initialBackoffMillis * (1L shl (attempt - 1))
                    retryDelay.waitMillis(result.failure.retryAfterMillis ?: exponential)
                    attempt++
                }
            }
        }
    }

    private fun cacheKey(analysis: RouteAnalysis): String = buildString {
        append("f6-occurrence-cache-v1/")
        append(analysis.fingerprint)
        append("/providers=")
        append(providers.map { it.id.name.lowercase() }.sorted().joinToString(","))
    }

    private fun deduplicate(records: List<DocumentedOccurrence>): List<DocumentedOccurrence> = records
        .associateBy { it.provider to it.providerRecordId }
        .values
        .sortedWith(compareBy(DocumentedOccurrence::provider, DocumentedOccurrence::providerRecordId))
}
