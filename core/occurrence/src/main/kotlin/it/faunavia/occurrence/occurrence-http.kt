package it.faunavia.occurrence

import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

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
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 5_000,
) : OccurrenceHttpClient {
    override fun get(url: String): OccurrenceHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val retryAfter = connection.getHeaderField("Retry-After")
            return OccurrenceHttpResponse(status, body, mapOf("Retry-After" to retryAfter.orEmpty()))
        } finally {
            connection.disconnect()
        }
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
