package it.faunavia.taxonomy

import it.faunavia.network.JsonHttpPolicy
import it.faunavia.network.JsonHttpTransport

import it.faunavia.domain.AppClock
import it.faunavia.domain.Provenance
import it.faunavia.domain.Taxon
import it.faunavia.domain.TaxonomicStatus
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
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
    connectTimeoutMillis: Int = 5_000,
    readTimeoutMillis: Int = 15_000,
) : GbifHttpClient {
    private val transport = JsonHttpTransport()
    private val policy = JsonHttpPolicy(connectTimeoutMillis, readTimeoutMillis)

    override fun get(url: String): String = transport.get(url, policy) { status ->
        if (status in 200..299) null else GbifHttpException("GBIF responded with HTTP $status")
    }.body
}

/** Searches common names before scientific autocomplete and resolves accepted backbone identities. */
class GbifTaxonomyProvider(
    private val http: GbifHttpClient,
    private val clock: AppClock,
) : TaxonomyProvider {
    companion object {
        private const val ENDPOINT = "https://api.gbif.org/v1/species"
        private const val BACKBONE = "d7dddbf4-2cf0-4f39-9b2a-bb099caae36c"
        private const val SOURCE = "GBIF Backbone Taxonomy"
        private const val VERSION = "GBIF Species API v1"
        private const val LICENSE = "GBIF API terms; downstream dataset licenses remain applicable"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun suggest(query: String, limit: Int): TaxonomyProviderResult = try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val commonRequest = "$ENDPOINT/search?q=$encoded&qField=VERNACULAR&highertaxon_key=1&datasetKey=$BACKBONE&limit=40"
        val commonResponse = json.parseToJsonElement(http.get(commonRequest)).jsonObject
        val commonRecords = requireNotNull(commonResponse["results"]) { "Missing search results" }.jsonArray
        val common = commonRecords.mapNotNull { resolve(it.jsonObject, query, "search (vernacular)") }
            .filter { it.taxon.isSelectable }
        val candidates = if (common.isNotEmpty()) common else {
            val request = "$ENDPOINT/suggest?q=$encoded&limit=${limit.coerceIn(1, 20)}"
            json.parseToJsonElement(http.get(request)).jsonArray
                .mapNotNull { resolve(it.jsonObject, query, "suggest (scientific)") }
                .filter { it.taxon.isSelectable }
        }
        if (candidates.isEmpty()) TaxonomyProviderResult.Empty else TaxonomyProviderResult.Success(candidates)
    } catch (error: SocketTimeoutException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.TIMEOUT)
    } catch (error: InterruptedIOException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.TIMEOUT)
    } catch (error: SerializationException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.MALFORMED_RESPONSE)
    } catch (error: IllegalArgumentException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.MALFORMED_RESPONSE)
    } catch (error: IOException) {
        TaxonomyProviderResult.Failure(TaxonomyFailure.NETWORK)
    }

    private fun resolve(record: JsonObject, query: String, operation: String): TaxonomyCandidate? =
        if (record.status() == "SYNONYM") resolveSynonym(record, query, operation)
        else candidateFrom(record, query, operation)

    private fun resolveSynonym(suggestion: JsonObject, query: String, operation: String): TaxonomyCandidate? {
        val acceptedKey = suggestion.string("acceptedKey") ?: suggestion.string("acceptedTaxonKey") ?: return null
        val accepted = json.parseToJsonElement(http.get("$ENDPOINT/$acceptedKey")).jsonObject
        val resolved = candidateFrom(accepted, query, operation) ?: return null
        return resolved.copy(aliases = resolved.aliases + suggestion.names())
    }

    private fun candidateFrom(record: JsonObject, query: String, operation: String): TaxonomyCandidate? {
        val key = record.string("key") ?: return null
        val scientificName = record.string("scientificName") ?: record.string("canonicalName") ?: return null
        val status = when (record.status()) {
            "ACCEPTED" -> TaxonomicStatus.ACCEPTED
            "SYNONYM" -> TaxonomicStatus.SYNONYM
            else -> TaxonomicStatus.DOUBTFUL
        }
        val taxon = Taxon(
            id = "gbif:$key",
            scientificName = scientificName,
            commonName = record.commonName(query),
            kingdom = record.string("kingdom") ?: "Unknown",
            status = status,
            rank = record.string("rank") ?: "UNRANKED",
            provenance = Provenance(
                source = SOURCE,
                recordId = key,
                query = "GBIF Species $operation: $query",
                retrievedAt = java.time.Instant.ofEpochMilli(clock.nowEpochMillis()),
                license = LICENSE,
                attribution = SOURCE,
                quality = "provider $operation",
                version = VERSION,
            ),
        )
        return TaxonomyCandidate(taxon, record.names())
    }

    private fun JsonObject.names(): List<String> = listOfNotNull(
        string("scientificName"),
        string("canonicalName"),
        string("vernacularName"),
    ) + vernaculars().mapNotNull { it.string("vernacularName") }

    private fun JsonObject.vernaculars(): List<JsonObject> = get("vernacularNames")?.jsonArray
        ?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.commonName(query: String): String? {
        val names = vernaculars()
        val italian = names.filter { it.string("language") in listOf("ita", "it") }
        val exact: (JsonObject) -> Boolean = { normalizeQuery(it.string("vernacularName").orEmpty()) == normalizeQuery(query) }
        return italian.firstOrNull(exact)?.string("vernacularName")
            ?: italian.firstOrNull()?.string("vernacularName")
            ?: string("vernacularName")
            ?: names.firstOrNull(exact)?.string("vernacularName")
            ?: names.firstOrNull { it.string("language") in listOf("eng", "en") }?.string("vernacularName")
            ?: names.firstOrNull()?.string("vernacularName")
    }

    private fun JsonObject.status(): String? = (string("taxonomicStatus") ?: string("status"))?.uppercase()

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
}
