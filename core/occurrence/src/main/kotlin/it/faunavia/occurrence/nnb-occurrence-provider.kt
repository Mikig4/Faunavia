package it.faunavia.occurrence

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.route.GeoBounds
import java.net.URLEncoder
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class NnbOccurrenceProviderConfig(
    val pageSize: Int = 100,
    val maxPagesPerArea: Int = 3,
) {
    init {
        require(pageSize in 1..1_000)
        require(maxPagesPerArea in 1..10)
    }
}

/**
 * NNB WFS fallback adapter. NNB does not expose a per-record reuse licence in this response,
 * therefore that limitation is retained in every normalized occurrence instead of guessed.
 */
class NnbOccurrenceProvider(
    private val http: OccurrenceHttpClient,
    private val clock: AppClock,
    private val config: NnbOccurrenceProviderConfig = NnbOccurrenceProviderConfig(),
) : OccurrenceProvider {
    override val id: OccurrenceProviderId = OccurrenceProviderId.NNB

    override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult = try {
        val records = buildList {
            scope.queryChunks.forEach { chunk -> addAll(searchBounds(chunk.key, chunk.bounds)) }
        }
        OccurrenceProviderResult.Success(records)
    } catch (failure: ProviderRequestException) {
        OccurrenceProviderResult.Failure(failure.failure)
    } catch (failure: Exception) {
        OccurrenceProviderResult.Failure(failure.toOccurrenceFailure(id))
    }

    private fun searchBounds(key: String, bounds: GeoBounds): List<DocumentedOccurrence> {
        val records = mutableListOf<DocumentedOccurrence>()
        var startIndex = 0
        repeat(config.maxPagesPerArea) {
            val response = http.get(requestUrl(bounds, startIndex))
            response.failure(id)?.let { throw ProviderRequestException(it) }
            val root = json.parseToJsonElement(response.body).jsonObject
            val page = root.requiredArray("features")
            records += page.mapNotNull { feature -> feature.jsonObject.toOccurrence(key, startIndex) }
            val returned = root.int("numberReturned") ?: page.size
            val matched = root.int("numberMatched")
            if (page.isEmpty() || returned == 0 || matched == null || startIndex + returned >= matched) return records
            startIndex += returned
        }
        return records
    }

    private fun requestUrl(bounds: GeoBounds, startIndex: Int): String = "$ENDPOINT?" + mapOf(
        "service" to "WFS",
        "version" to "2.0.0",
        "request" to "GetFeature",
        "typeNames" to "nnb:Osservazioni_puntuali",
        "bbox" to "${bounds.west},${bounds.south},${bounds.east},${bounds.north},EPSG:4326",
        "count" to config.pageSize.toString(),
        "startIndex" to startIndex.toString(),
        "outputFormat" to "application/json",
    ).entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    private fun JsonObject.toOccurrence(areaKey: String, startIndex: Int): DocumentedOccurrence? {
        val properties = get("properties")?.jsonObject ?: return null
        val recordId = properties.string("id_osservazione") ?: string("id")?.substringAfterLast('.') ?: return null
        val scientificName = properties.string("nome_scientifico") ?: return null
        val coordinates = get("geometry")?.jsonObject?.get("coordinates")?.jsonArray
        val location = coordinates?.takeIf { it.size >= 2 }?.let { coordinate ->
            pointOrNull(coordinate[1].jsonPrimitive.doubleOrNull, coordinate[0].jsonPrimitive.doubleOrNull)
        }
        val sourceDataset = properties.string("banca_dati") ?: "NNB osservazioni puntuali"
        return DocumentedOccurrence(
            id = "nnb:$recordId",
            provider = id,
            providerRecordId = recordId,
            taxonId = null,
            scientificName = scientificName,
            observedOn = properties.string("anno"),
            location = location,
            coordinateUncertaintyMeters = null,
            sourceUrl = NNB_DATA_PAGE,
            provenance = Provenance(
                source = "Network Nazionale della Biodiversità (ISPRA)",
                recordId = recordId,
                query = "NNB WFS occurrence search; area=$areaKey; spatialMode=bbox; startIndex=$startIndex",
                retrievedAt = Instant.ofEpochMilli(clock.nowEpochMillis()),
                license = "NNB per-record license not exposed; personal/non-commercial use must be verified per dataset",
                attribution = "$sourceDataset; Network Nazionale della Biodiversità (ISPRA)",
                quality = "NNB WFS point record; coordinate uncertainty not exposed",
                version = "NNB WFS 2.0.0",
            ),
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun JsonObject.requiredArray(key: String): JsonArray = get(key)?.jsonArray
        ?: throw SerializationException("Missing NNB '$key' array.")
    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

    private class ProviderRequestException(val failure: OccurrenceProviderFailure) : RuntimeException()

    private companion object {
        const val ENDPOINT = "https://geoserver.nnb.isprambiente.it/geoserver/nnb/ows"
        const val NNB_DATA_PAGE = "https://www.nnb.isprambiente.it/it/usa-i-dati"
        val json = Json { ignoreUnknownKeys = true }
    }
}
