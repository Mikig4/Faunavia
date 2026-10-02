package it.faunavia.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class JsonHttpPolicy(
    val connectTimeoutMillis: Int = 5_000,
    val readTimeoutMillis: Int = 5_000,
    val userAgent: String? = null,
    val maxBodyBytes: Int? = null,
    val maxBodyChars: Int? = null,
    val bodyTooLargeMessage: String = "HTTP response too large",
) {
    init {
        require(connectTimeoutMillis >= 0 && readTimeoutMillis >= 0)
        require(maxBodyBytes == null || maxBodyBytes >= 0)
        require(maxBodyChars == null || maxBodyChars >= 0)
        require(maxBodyBytes == null || maxBodyChars == null) { "Choose a byte or character limit." }
    }
}

data class JsonHttpResponse(val status: Int, val body: String, val retryAfter: String?)

/** Blocking JSON GET transport. Provider adapters retain their status/error and size policies. */
class JsonHttpTransport(
    private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) {
    fun get(url: String, policy: JsonHttpPolicy = JsonHttpPolicy(),
        statusFailure: (Int) -> IOException? = { null }): JsonHttpResponse {
        val connection = openConnection(url)
        try {
            connection.connectTimeout = policy.connectTimeoutMillis
            connection.readTimeout = policy.readTimeoutMillis
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            policy.userAgent?.let { connection.setRequestProperty("User-Agent", it) }
            val status = connection.responseCode
            statusFailure(status)?.let { throw it }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readBody(it, policy) }.orEmpty()
            return JsonHttpResponse(status, body, connection.getHeaderField("Retry-After"))
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(input: InputStream, policy: JsonHttpPolicy): String {
        policy.maxBodyBytes?.let { limit ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > limit - output.size()) throw IOException(policy.bodyTooLargeMessage)
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
        input.bufferedReader().use { reader ->
            val limit = policy.maxBodyChars ?: return reader.readText()
            val content = StringBuilder()
            val buffer = CharArray(8_192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count > limit - content.length) throw IOException(policy.bodyTooLargeMessage)
                content.append(buffer, 0, count)
            }
            return content.toString()
        }
    }
}
