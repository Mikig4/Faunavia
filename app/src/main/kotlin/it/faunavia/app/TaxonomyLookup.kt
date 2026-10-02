package it.faunavia.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import it.faunavia.taxonomy.MINIMUM_TAXON_QUERY_LENGTH
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchResult
import it.faunavia.taxonomy.normalizeQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class TaxonomyLookupState {
    var result by mutableStateOf<TaxonomySearchResult>(TaxonomySearchResult.AwaitingQuery())
        private set
    var loading by mutableStateOf(false)
        private set
    var unexpectedFailure by mutableStateOf(false)
        private set
    var refresh by mutableIntStateOf(0)
        private set
    private var generation = 0

    fun retry() { refresh++ }

    fun begin(active: Boolean): Int {
        generation++
        result = TaxonomySearchResult.AwaitingQuery()
        unexpectedFailure = false
        loading = active
        return generation
    }

    fun complete(request: Int, value: TaxonomySearchResult) {
        if (request == generation) result = value
    }

    fun fail(request: Int) {
        if (request == generation) unexpectedFailure = true
    }

    fun finish(request: Int) {
        if (request == generation) loading = false
    }
}

/** Shared debounce and request lifecycle; each screen owns its copy and selection behavior. */
@Composable
internal fun rememberTaxonomyLookup(search: TaxonomySearch, query: String,
    enabled: Boolean = true, debounceMillis: Long = 350): TaxonomyLookupState {
    require(debounceMillis >= 0) { "The catalogue debounce cannot be negative." }
    val state = remember { TaxonomyLookupState() }
    val normalized = normalizeQuery(query)
    LaunchedEffect(search, normalized, enabled, state.refresh) {
        val active = enabled && normalized.length >= MINIMUM_TAXON_QUERY_LENGTH
        val generation = state.begin(active)
        if (!active) return@LaunchedEffect
        try {
            delay(debounceMillis)
            val result = withContext(Dispatchers.IO) { search.search(normalized) }
            state.complete(generation, result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.fail(generation)
        } finally {
            state.finish(generation)
        }
    }
    return state
}
