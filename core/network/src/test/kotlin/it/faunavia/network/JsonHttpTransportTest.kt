package it.faunavia.network

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class JsonHttpTransportTest {
    @Test fun configuresRequestAndClosesResponseAndConnection() {
        val connection = FakeConnection(200, "{\"name\":\"è\"}", retryAfter = "2")
        val result = JsonHttpTransport { connection }.get("unused", JsonHttpPolicy(123, 456, "Faunavia/test"))
        assertEquals(200, result.status)
        assertEquals("{\"name\":\"è\"}", result.body)
        assertEquals("2", result.retryAfter)
        assertEquals("GET", connection.requestMethod)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals("Faunavia/test", connection.getRequestProperty("User-Agent"))
        assertEquals(123, connection.connectTimeout)
        assertEquals(456, connection.readTimeout)
        assertTrue(connection.stream.closed)
        assertTrue(connection.disconnected)
    }

    @Test fun returnsErrorBodyAndOptionalRetryAfterWithoutForcingSuccess() {
        val connection = FakeConnection(429, "rate limited", "60")
        val result = JsonHttpTransport { connection }.get("unused")
        assertEquals(429, result.status)
        assertEquals("rate limited", result.body)
        assertEquals("60", result.retryAfter)
        assertTrue(connection.disconnected)
        val missing = FakeConnection(503, "", missingErrorStream = true)
        assertEquals("", JsonHttpTransport { missing }.get("unused").body)
        assertNull(missing.getRequestProperty("User-Agent"))
    }

    @Test fun providerStatusFailureHappensBeforeReadingBodyAndDisconnects() {
        val connection = FakeConnection(503, "error")
        val failure = IOException("provider unavailable")
        assertSame(failure, assertThrows(IOException::class.java) {
            JsonHttpTransport { connection }.get("unused") { failure }
        })
        assertFalse(connection.bodyRequested)
        assertTrue(connection.disconnected)
    }

    @Test fun byteAndCharacterLimitsRemainDistinctAndAcceptExactBoundaries() {
        val text = "èè"
        assertEquals(text, JsonHttpTransport { FakeConnection(200, text) }
            .get("unused", JsonHttpPolicy(maxBodyBytes = 4)).body)
        assertEquals(text, JsonHttpTransport { FakeConnection(200, text) }
            .get("unused", JsonHttpPolicy(maxBodyChars = 2)).body)
        listOf(JsonHttpPolicy(maxBodyBytes = 3), JsonHttpPolicy(maxBodyChars = 1)).forEach { policy ->
            val connection = FakeConnection(200, text)
            assertThrows(IOException::class.java) { JsonHttpTransport { connection }.get("unused", policy) }
            assertTrue(connection.stream.closed)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun readFailureRetainsExceptionAndReleasesResources() {
        val connection = FakeConnection(200, "unused", failRead = true)
        assertEquals("read failed", assertThrows(IOException::class.java) {
            JsonHttpTransport { connection }.get("unused")
        }.message)
        assertTrue(connection.stream.closed)
        assertTrue(connection.disconnected)
    }

    private class TrackedStream(body: String, private val failRead: Boolean) : InputStream() {
        private val delegate = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        var closed = false
        override fun read(): Int {
            if (failRead) throw IOException("read failed")
            return delegate.read()
        }
        override fun close() { closed = true; delegate.close() }
    }

    private class FakeConnection(private val status: Int, body: String, private val retryAfter: String? = null,
        private val missingErrorStream: Boolean = false, failRead: Boolean = false) : HttpURLConnection(URL("http://localhost/")) {
        val stream = TrackedStream(body, failRead)
        var disconnected = false
        var bodyRequested = false
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream { bodyRequested = true; return stream }
        override fun getErrorStream(): InputStream? { bodyRequested = true; return if (missingErrorStream) null else stream }
        override fun getHeaderField(name: String): String? = if (name == "Retry-After") retryAfter else null
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
