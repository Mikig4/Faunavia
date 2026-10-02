package it.faunavia.occurrence

import it.faunavia.network.JsonHttpPolicy
import it.faunavia.network.JsonHttpTransport

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

data class OccurrenceHttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

interface OccurrenceHttpClient {
    fun get(url: String): OccurrenceHttpResponse
}

/** Small blocking client: Android invokes the gateway from an IO dispatcher. */
class UrlConnectionOccurrenceHttpClient(
    connectTimeoutMillis: Int = 5_000,
    readTimeoutMillis: Int = 5_000,
) : OccurrenceHttpClient {
    private val transport = JsonHttpTransport()
    private val policy = JsonHttpPolicy(connectTimeoutMillis, readTimeoutMillis)

    override fun get(url: String): OccurrenceHttpResponse = transport.get(url, policy).let {
        OccurrenceHttpResponse(it.status, it.body, mapOf("Retry-After" to it.retryAfter.orEmpty()))
    }
}

internal fun Throwable.toOccurrenceFailure(
    provider: OccurrenceProviderId,
): OccurrenceProviderFailure = when (this) {
    is SocketTimeoutException, is InterruptedIOException -> OccurrenceProviderFailure(
        provider,
        OccurrenceFailureReason.TIMEOUT,
        "The provider request timed out.",
    )
    is IOException -> OccurrenceProviderFailure(provider, OccurrenceFailureReason.NETWORK, "The provider could not be reached.")
    else -> OccurrenceProviderFailure(provider, OccurrenceFailureReason.MALFORMED_RESPONSE, "The provider response was invalid.")
}

internal fun OccurrenceHttpResponse.failure(
    provider: OccurrenceProviderId,
): OccurrenceProviderFailure? = when (status) {
    in 200..299 -> null
    429 -> OccurrenceProviderFailure(
        provider,
        OccurrenceFailureReason.RATE_LIMITED,
        "The provider rate-limited this search.",
        headers["Retry-After"].toRetryAfterMillis(),
    )
    in 500..599 -> OccurrenceProviderFailure(
        provider,
        OccurrenceFailureReason.SERVICE_UNAVAILABLE,
        "The provider responded with HTTP $status.",
    )
    else -> OccurrenceProviderFailure(
        provider,
        OccurrenceFailureReason.UNEXPECTED_RESPONSE,
        "The provider responded with HTTP $status.",
    )
}

private fun String?.toRetryAfterMillis(): Long? = this?.trim()?.toLongOrNull()?.takeIf { it >= 0L }?.times(1_000L)
