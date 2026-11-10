package it.faunavia.app

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.faunavia.domain.Trip
import it.faunavia.exploration.OutingSearch
import it.faunavia.exploration.OutingSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OutingSearchViewModel : ViewModel() {
    val result = mutableStateOf<OutingSearchResult?>(null)
    val loading = mutableStateOf(false)
    val scopeKey = mutableStateOf("")
    private var generation = 0
    private var work: Job? = null

    /** Called by explicit buttons only; configuration restoration never launches a request. */
    fun search(trip: Trip, key: String, source: OutingSearch) {
        stop()
        val token = generation
        scopeKey.value = key
        result.value = null
        loading.value = true
        work = viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { source.search(trip) }
                if (generation == token) result.value = response
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (generation == token) result.value = OutingSearchResult(emptyList(),
                    "Ricerca online non riuscita. Riprova; le uscite salvate restano disponibili.")
            } finally { if (generation == token) loading.value = false }
        }
    }

    fun stop() {
        generation++
        work?.cancel()
        loading.value = false
    }
}
