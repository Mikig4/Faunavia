package it.faunavia.app

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.faunavia.exploration.ExplorationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Large results and running work stay outside instance-state bundles across configuration changes. */
internal class AnalysisViewModel : ViewModel() {
    val result = mutableStateOf<ExplorationResult?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val scopeKey = mutableStateOf("")
    private var request: String? = null
    private var work: Job? = null

    fun start(token: String, scope: String = token, failureMessage: (Throwable) -> String,
        analyze: suspend () -> ExplorationResult) {
        if (request == token) return
        work?.cancel()
        request = token
        scopeKey.value = scope
        result.value = null
        error.value = null
        loading.value = true
        work = viewModelScope.launch {
            try {
                val completed = withContext(Dispatchers.IO) { analyze() }
                if (request == token) result.value = completed
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (request == token) error.value = failureMessage(failure)
            } finally {
                if (request == token) loading.value = false
            }
        }
    }

    fun clear() {
        request = null
        work?.cancel()
        result.value = null
        loading.value = false
        error.value = null
        scopeKey.value = ""
    }
}
