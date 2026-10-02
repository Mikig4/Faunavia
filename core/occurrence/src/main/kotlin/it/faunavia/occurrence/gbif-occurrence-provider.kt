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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GbifOccurrenceProviderConfig(
    val pageSize: Int = 100,
    val maxPagesPerArea: Int = 3,
    val maxPolygonVertices: Int = 64,
) {
    init {
        require(pageSize in 1..300)
        require(maxPagesPerArea in 1..10)
        require(maxPolygonVertices >= 5)
    }
}

/** GBIF Occurrence API v1 adapter. It owns all HTTP and query-shape choices. */
class GbifOccurrenceProvider(
    private val http: OccurrenceHttpClient,
    private val clock: AppClock,
    private val config: GbifOccurrenceProviderConfig = GbifOccurrenceProviderConfig(),
) : OccurrenceProvider {
    override val id: OccurrenceProviderId = OccurrenceProviderId.GBIF

    override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult = try {
        val records = buildList {
            spatialPlans(scope).forEach { plan -> addAll(searchPlan(plan)) }
        }
        OccurrenceProviderResult.Success(records)
    } catch (failure: ProviderRequestException) {
        OccurrenceProviderResult.Failure(failure.failure)
    } catch (failure: Exception) {
        OccurrenceProviderResult.Failure(failure.toOccurrenceFailure(id))
    }

    private fun spatialPlans(scope: OccurrenceSearchScope): List<GbifSpatialPlan> {
        val polygonPlans = scope.corridorPortions.map { portion ->
            GbifSpatialPlan.Polygon(portion.key, portion.polygon)
        }
        return if (polygonPlans.all { it.points.size <= config.maxPolygonVertices }) polygonPlans
        else scope.queryChunks.map { chunk -> GbifSpatialPlan.BoundingBox(chunk.key, chunk.bounds) }
    }

    private fun searchPlan(plan: GbifSpatialPlan): List<DocumentedOccurrence> {
        val records = mutableListOf<DocumentedOccurrence>()
        var offset = 0
        repeat(config.maxPagesPerArea) {
            val response = http.get(requestUrl(plan, offset))
            response.failure(id)?.let { throw ProviderRequestException(it) }
            val root = json.parseToJsonElement(response.body).jsonObject
            val page = root.requiredArray("results")
            records += page.mapNotNull { result -> result.jsonObject.toOccurrence(plan, offset) }
            val endOfRecords = root["endOfRecords"]?.jsonPrimitive?.booleanOrNull
            if (page.isEmpty() || endOfRecords == true || page.size < config.pageSize) return records
            offset += page.size
        }
        return records
    }

    private fun requestUrl(plan: GbifSpatialPlan, offset: Int): String = url(
        mapOf(
            "taxon_key" to "1",
            "occurrence_status" to "present",
            "has_coordinate" to "true",
            "has_geospatial_issue" to "false",
            plan.parameterName to plan.parameterValue,
            "limit" to config.pageSize.toString(),
            "offset" to offset.toString(),
        ),
    )

    private fun JsonObject.toOccurrence(plan: GbifSpatialPlan, offset: Int): DocumentedOccurrence? {
        val recordId = string("key") ?: return null
        val scientificName = string("acceptedScientificName") ?: string("species") ?: string("scientificName") ?: return null
        val coordinate = pointOrNull(double("decimalLatitude"), double("decimalLongitude"))
        val issues = get("issues")?.jsonArray?.joinToString(",") { it.jsonPrimitive.content } ?: "none"
        val dataset = string("datasetName") ?: "GBIF dataset not specified"
        val taxonKey = string("acceptedTaxonKey") ?: string("speciesKey") ?: string("taxonKey")
        return DocumentedOccurrence(
            id = "gbif:$recordId",
            provider = id,
            providerRecordId = recordId,
            taxonId = taxonKey?.let { "gbif:$it" },
            scientificName = scientificName,
            observedOn = string("eventDate") ?: string("year"),
            location = coordinate,
            coordinateUncertaintyMeters = double("coordinateUncertaintyInMeters")?.takeIf { it >= 0.0 },
            sourceUrl = "https://www.gbif.org/occurrence/$recordId",
            provenance = Provenance(
                source = "GBIF",
                recordId = recordId,
                query = "GBIF occurrence search; ${plan.describe()}; offset=$offset",
                retrievedAt = Instant.ofEpochMilli(clock.nowEpochMillis()),
                license = string("license") ?: "Not provided by the GBIF occurrence response",
                attribution = "$dataset; GBIF occurrence $recordId",
                quality = "basisOfRecord=${string("basisOfRecord") ?: "unspecified"}; issues=$issues",
                version = "GBIF Occurrence API v1",
            ),
        )
    }

    private fun url(parameters: Map<String, String>): String = "$ENDPOINT?" + parameters.entries.joinToString("&") {
        "${encode(it.key)}=${encode(it.value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun JsonObject.requiredArray(key: String): JsonArray = get(key)?.jsonArray
        ?: throw SerializationException("Missing GBIF '$key' array.")
    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.double(key: String): Double? = get(key)?.jsonPrimitive?.doubleOrNull

    private sealed interface GbifSpatialPlan {
        val key: String
        val parameterName: String
        val parameterValue: String
        fun describe(): String

        data class Polygon(override val key: String, val points: List<GeoPoint>) : GbifSpatialPlan {
            override val parameterName: String = "geometry"
            override val parameterValue: String = "POLYGON((" + points.joinToString(",") {
                "${it.longitude} ${it.latitude}"
            } + "))"
            override fun describe(): String = "area=$key; spatialMode=polygon"
        }

        data class BoundingBox(override val key: String, val bounds: GeoBounds) : GbifSpatialPlan {
            override val parameterName: String = "geometry"
            override val parameterValue: String = "POLYGON((" + listOf(
                "${bounds.west} ${bounds.south}",
                "${bounds.east} ${bounds.south}",
                "${bounds.east} ${bounds.north}",
                "${bounds.west} ${bounds.north}",
                "${bounds.west} ${bounds.south}",
            ).joinToString(",") + "))"
            override fun describe(): String = "area=$key; spatialMode=bbox"
        }
    }

    private class ProviderRequestException(val failure: OccurrenceProviderFailure) : RuntimeException()

    private companion object {
        const val ENDPOINT = "https://api.gbif.org/v1/occurrence/search"
        val json = Json { ignoreUnknownKeys = true }
    }
}

internal fun pointOrNull(latitude: Double?, longitude: Double?): GeoPoint? = try {
    if (latitude == null || longitude == null) null else GeoPoint(latitude, longitude)
} catch (_: IllegalArgumentException) {
    null
}
