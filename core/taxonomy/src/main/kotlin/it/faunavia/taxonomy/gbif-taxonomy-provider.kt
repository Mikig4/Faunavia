package it.faunavia.taxonomy

import it.faunavia.domain.AppClock
import it.faunavia.domain.Provenance
import it.faunavia.domain.Taxon
import it.faunavia.domain.TaxonomicStatus
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface GbifHttpClient {
    fun get(url: String): String
}

class GbifHttpException(message: String) : IOException(message)

/** Small blocking client: the app invokes search from Dispatchers.IO. */
class UrlConnectionGbifHttpClient(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 5_000,
) : GbifHttpClient {
    override fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw GbifHttpException("GBIF responded with HTTP $status")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** GBIF Species API v1 adapter. It resolves a suggested synonym to its accepted backbone key. */
class GbifTaxonomyProvider(
    private val http: GbifHttpClient,
    private val clock: AppClock,
) : TaxonomyProvider {
    companion object {
        private const val ENDPOINT = "https://api.gbif.org/v1/species"
        private const val SOURCE = "GBIF Backbone Taxonomy"
        private const val VERSION = "GBIF Species API v1"
        private const val LICENSE = "GBIF API terms; downstream dataset licenses remain applicable"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun suggest(query: String, limit: Int): TaxonomyProviderResult = try {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        val request = "$ENDPOINT/suggest?q=$encoded&limit=${limit.coerceIn(1, 20)}"
        val suggestions = json.parseToJsonElement(http.get(request)).jsonArray
        val candidates = suggestions.mapNotNull { item ->
            val suggestion = item.jsonObject
            when (suggestion.string("status")?.uppercase()) {
                "SYNONYM" -> resolveSynonym(suggestion, query)
                else -> candidateFrom(suggestion, query)
            }
        }
        if (candidates.isEmpty()) TaxonomyProviderResult.Empty else TaxonomyProviderResult.Success(candidates)
    } catch (_: SocketTimeoutException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.TIMEOUT)
    } catch (_: InterruptedIOException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.TIMEOUT)
    } catch (_: SerializationException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.MALFORMED_RESPONSE)
    } catch (_: IllegalArgumentException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.MALFORMED_RESPONSE)
    } catch (_: IOException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.NETWORK)
    }

    private fun resolveSynonym(suggestion: JsonObject, query: String): TaxonomyCandidate? {
        val acceptedKey = suggestion.string("acceptedKey") ?: return null
        val accepted = json.parseToJsonElement(http.get("$ENDPOINT/$acceptedKey")).jsonObject
        val resolved = candidateFrom(accepted, query) ?: return null
        return resolved.copy(aliases = resolved.aliases + suggestion.names())
    }

    private fun candidateFrom(record: JsonObject, query: String): TaxonomyCandidate? {
        val key = record.string("key") ?: return null
        val scientificName = record.string("scientificName") ?: record.string("canonicalName") ?: return null
        val status = when (record.string("status")?.uppercase()) {
            "ACCEPTED" -> TaxonomicStatus.ACCEPTED
            "SYNONYM" -> TaxonomicStatus.SYNONYM
            else -> TaxonomicStatus.DOUBTFUL
        }
        val taxon = Taxon(
            id = "gbif:$key",
            scientificName = scientificName,
            commonName = record.string("vernacularName"),
            kingdom = record.string("kingdom") ?: "Unknown",
            status = status,
            rank = record.string("rank") ?: "UNRANKED",
            provenance = Provenance(
                source = SOURCE,
                recordId = key,
                query = "GBIF Species suggest: $query",
                retrievedAt = java.time.Instant.ofEpochMilli(clock.nowEpochMillis()),
                license = LICENSE,
                attribution = SOURCE,
                quality = "provider autocomplete",
                version = VERSION,
            ),
        )
        return TaxonomyCandidate(taxon, record.names())
    }

    private fun JsonObject.names(): List<String> = listOfNotNull(
        string("scientificName"),
        string("canonicalName"),
        string("vernacularName"),
    )

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
}
