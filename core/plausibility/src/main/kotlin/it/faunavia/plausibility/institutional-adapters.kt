package it.faunavia.plausibility

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.route.GeoBounds
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlausibilityHttpResponse(
    val status: Int,
    val body: String,
)

interface PlausibilityHttpClient {
    fun get(url: String): PlausibilityHttpResponse
}

/** Small blocking client; callers must invoke an adapter from an IO dispatcher. */
class UrlConnectionPlausibilityHttpClient(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 5_000,
) : PlausibilityHttpClient {
    override fun get(url: String): PlausibilityHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return PlausibilityHttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

enum class InstitutionalSourceFailureReason {
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    UNEXPECTED_RESPONSE,
    MALFORMED_RESPONSE,
    NETWORK,
}

data class InstitutionalSourceFailure(
    val source: String,
    val reason: InstitutionalSourceFailureReason,
    val detail: String,
) {
    init { require(source.isNotBlank() && detail.isNotBlank()) }
}

sealed interface InstitutionalSourceResult<out T> {
    data class Success<T>(val value: T) : InstitutionalSourceResult<T>
    data class Failure(val failure: InstitutionalSourceFailure) : InstitutionalSourceResult<Nothing>
}

enum class EeaArticleDirective(
    val endpoint: String,
    val sourceLabel: String,
    val dataset: String,
    val sourceVersion: String,
) {
    ARTICLE_12(
        endpoint = "https://bio.discomap.eea.europa.eu/arcgis/rest/services/Article_12/ART12_birds_2019_2024_public/MapServer/0/query",
        sourceLabel = "EEA Article 12",
        dataset = "Article 12 birds 2019-2024 public",
        sourceVersion = "2019-2024 reporting cycle",
    ),
    ARTICLE_17(
        endpoint = "https://bio.discomap.eea.europa.eu/arcgis/rest/services/Article17/ART17_2019_2024_public/MapServer/6/query",
        sourceLabel = "EEA Article 17",
        dataset = "Article 17 species 2019-2024 public",
        sourceVersion = "2019-2024 reporting cycle",
    ),
}

data class EeaArticleAdapterConfig(
    val directive: EeaArticleDirective,
    val pageSize: Int = 200,
) {
    init { require(pageSize in 1..2_000) }
}

/** Raw Article 12 season codes are retained as facts; no calendar month is inferred from a code. */
data class InstitutionalSeasonCode(
    val taxon: TaxonReference,
    val code: String,
    val label: String?,
    val provenance: Provenance,
) {
    init { require(code.isNotBlank()) }
}

data class EeaArticleEvidence(
    val ranges: List<RangeEvidence>,
    val seasonCodes: List<InstitutionalSeasonCode>,
)

interface InstitutionalRangeAdapter {
    suspend fun query(bounds: GeoBounds): InstitutionalSourceResult<EeaArticleEvidence>
}

/** Bounded EEA Article 12/17 envelope adapter. Reporting range is never treated as a sighting. */
class EeaArticleRangeAdapter(
    private val http: PlausibilityHttpClient,
    private val clock: AppClock,
    private val config: EeaArticleAdapterConfig,
) : InstitutionalRangeAdapter {
    override suspend fun query(bounds: GeoBounds): InstitutionalSourceResult<EeaArticleEvidence> = try {
        val response = http.get(requestUrl(bounds))
        response.toFailure(config.directive.sourceLabel)?.let { return InstitutionalSourceResult.Failure(it) }
        val rows = response.body.articleRows()
        val records = rows.mapNotNull { row -> row.toEvidence(bounds) }
        InstitutionalSourceResult.Success(
            EeaArticleEvidence(
                ranges = records.map { it.range },
                seasonCodes = records.mapNotNull { it.season },
            ),
        )
    } catch (_: IOException) {
        InstitutionalSourceResult.Failure(networkFailure(config.directive.sourceLabel))
    } catch (_: Exception) {
        InstitutionalSourceResult.Failure(malformedFailure(config.directive.sourceLabel))
    }

    private fun requestUrl(bounds: GeoBounds): String = "${config.directive.endpoint}?" + mapOf(
        "where" to "1=1",
        "geometry" to "${bounds.west},${bounds.south},${bounds.east},${bounds.north}",
        "geometryType" to "esriGeometryEnvelope",
        "spatialRel" to "esriSpatialRelIntersects",
        "outFields" to "*",
        "returnGeometry" to "false",
        "resultRecordCount" to config.pageSize.toString(),
        "f" to "json",
    ).entries.joinToString("&") { "${it.key.urlEncode()}=${it.value.urlEncode()}" }

    private fun JsonObject.toEvidence(bounds: GeoBounds): ArticleRecord? {
        val scientificName = string("speciesName") ?: string("scientific_name") ?: return null
        val speciesCode = string("speciesCode") ?: string("species_code") ?: return null
        val region = string("biogeographicRegion") ?: string("biogeographic_region") ?: "not-reported"
        val taxon = TaxonReference(
            id = "${config.directive.name.lowercase()}:$speciesCode:$region",
            scientificName = scientificName,
        )
        val provenance = Provenance(
            source = config.directive.sourceLabel,
            recordId = "$speciesCode:$region",
            query = "${config.directive.sourceLabel} envelope range; bbox=${bounds.describe()}; spatialMode=envelope",
            retrievedAt = Instant.ofEpochMilli(clock.nowEpochMillis()),
            license = "EEA data policy; dataset-specific notices prevail",
            attribution = "European Environment Agency and reporting countries",
            quality = "institutional reporting geometry; ${string("assessment") ?: "assessment not reported"}",
            version = config.directive.sourceVersion,
        )
        val seasonCode = string("seasonCode")
        return ArticleRecord(
            range = RangeEvidence(taxon, RangeRelation.INTERSECTS, provenance),
            season = seasonCode?.let { InstitutionalSeasonCode(taxon, it, string("season"), provenance) },
        )
    }

    private data class ArticleRecord(
        val range: RangeEvidence,
        val season: InstitutionalSeasonCode?,
    )
}

data class MaesHabitatMatrixConfig(
    val endpoint: String,
    val sourceVersion: String = "2014",
) {
    init { require(endpoint.startsWith("https://")) }
}

/** Adapter for a versioned MAES species–ecosystem table, not for CLCplus class names. */
class MaesHabitatMatrixAdapter(
    private val http: PlausibilityHttpClient,
    private val clock: AppClock,
    private val config: MaesHabitatMatrixConfig,
) {
    suspend fun load(): InstitutionalSourceResult<List<HabitatAssociation>> = try {
        val response = http.get(config.endpoint)
        response.toFailure("EEA MAES")?.let { return InstitutionalSourceResult.Failure(it) }
        InstitutionalSourceResult.Success(response.body.maesRows().mapNotNull(::toAssociation))
    } catch (_: IOException) {
        InstitutionalSourceResult.Failure(networkFailure("EEA MAES"))
    } catch (_: Exception) {
        InstitutionalSourceResult.Failure(malformedFailure("EEA MAES"))
    }

    private fun toAssociation(row: JsonObject): HabitatAssociation? {
        val scientificName = row.string("speciesName") ?: return null
        val ecosystem = row.string("ecosystem") ?: return null
        val association = when (row.string("association")) {
            "P" -> HabitatAssociationStrength.PREFERRED
            "S" -> HabitatAssociationStrength.SUITABLE
            "O" -> HabitatAssociationStrength.OCCASIONAL
            else -> return null
        }
        val recordId = row.string("recordId") ?: "${canonicalScientificName(scientificName)}:$ecosystem"
        return HabitatAssociation(
            taxon = TaxonReference("eea-maes:$recordId", scientificName),
            ecosystem = ecosystem,
            strength = association,
            provenance = Provenance(
                source = "European Environment Agency",
                recordId = "maes:$recordId",
                query = "EEA MAES species-habitat matrix; spatialMode=static-table",
                retrievedAt = Instant.ofEpochMilli(clock.nowEpochMillis()),
                license = "EEA data policy; acknowledge EEA and original data providers",
                attribution = "European Environment Agency",
                quality = "institutional matrix; sourceVersion=${config.sourceVersion}",
                version = config.sourceVersion,
            ),
        )
    }
}

/**
 * Samples an injected, documented CLCplus product endpoint. The 2021 fixture is accepted only as
 * a technical parsing fixture: [LandCoverSample.equivalentToRequiredProduct] stays false for it.
 */
data class ClcplusLandCoverConfig(
    val endpoint: String,
    val productVersion: String,
    val equivalentToRequiredProduct: Boolean,
    val maximumSamples: Int = 100,
) {
    init {
        require(endpoint.startsWith("https://") && productVersion.isNotBlank())
        require(maximumSamples in 1..500)
    }
}

class ClcplusLandCoverAdapter(
    private val http: PlausibilityHttpClient,
    private val clock: AppClock,
    private val config: ClcplusLandCoverConfig,
) {
    suspend fun sample(points: List<GeoPoint>): InstitutionalSourceResult<List<LandCoverSample>> = try {
        require(points.size <= config.maximumSamples) { "CLCplus sample limit exceeded." }
        val samples = points.mapNotNull { point ->
            val response = http.get(requestUrl(point))
            response.toFailure("Copernicus Land Monitoring Service")?.let { throw SourceRequestException(it) }
            response.body.singleClcplusValueOrNull()?.let { value -> sample(point, value) }
        }
        InstitutionalSourceResult.Success(samples)
    } catch (failure: SourceRequestException) {
        InstitutionalSourceResult.Failure(failure.failure)
    } catch (_: IOException) {
        InstitutionalSourceResult.Failure(networkFailure("Copernicus Land Monitoring Service"))
    } catch (_: Exception) {
        InstitutionalSourceResult.Failure(malformedFailure("Copernicus Land Monitoring Service"))
    }

    /** Supports the F0 versioned envelope in deterministic tests without granting it ecological equivalence. */
    fun parseFixtureEnvelope(body: String): List<LandCoverSample> = body.clcplusFixtureRows().mapNotNull { row ->
        val coordinates = row["point"]?.jsonArray ?: return@mapNotNull null
        if (coordinates.size < 2) return@mapNotNull null
        val point = runCatching {
            GeoPoint(coordinates[1].jsonPrimitive.content.toDouble(), coordinates[0].jsonPrimitive.content.toDouble())
        }.getOrNull() ?: return@mapNotNull null
        val value = row.string("value") ?: return@mapNotNull null
        sample(point, value)
    }

    private fun requestUrl(point: GeoPoint): String = "${config.endpoint}?" + mapOf(
        "geometry" to "${point.longitude},${point.latitude}",
        "geometryType" to "esriGeometryPoint",
        "returnGeometry" to "false",
        "f" to "json",
    ).entries.joinToString("&") { "${it.key.urlEncode()}=${it.value.urlEncode()}" }

    private fun sample(point: GeoPoint, rawClass: String): LandCoverSample = LandCoverSample(
        location = point,
        rawClass = rawClass,
        productVersion = config.productVersion,
        equivalentToRequiredProduct = config.equivalentToRequiredProduct,
        provenance = Provenance(
            source = "Copernicus Land Monitoring Service",
            recordId = "clcplus:${config.productVersion}:${point.longitude},${point.latitude}",
            query = "CLCplus point sample; point=${point.longitude},${point.latitude}; spatialMode=point",
            retrievedAt = Instant.ofEpochMilli(clock.nowEpochMillis()),
            license = "Copernicus Land Monitoring Service data: free and open with source acknowledgement",
            attribution = "European Union, Copernicus Land Monitoring Service",
            quality = "raw CLCplus class; productVersion=${config.productVersion}",
            version = config.productVersion,
        ),
    )

    private class SourceRequestException(val failure: InstitutionalSourceFailure) : RuntimeException()
}

private fun String.articleRows(): List<JsonObject> {
    val root = json.parseToJsonElement(this).jsonObject
    val fixtureRows = root["response"]?.jsonObject?.get("extractedRows")?.jsonArray
    val liveRows = root["features"]?.jsonArray?.mapNotNull { it.jsonObject["attributes"]?.jsonObject }
    return fixtureRows?.map { it.jsonObject } ?: liveRows.orEmpty()
}

private fun String.maesRows(): List<JsonObject> {
    val root = json.parseToJsonElement(this).jsonObject
    return root["response"]?.jsonObject?.get("rows")?.jsonArray?.map { it.jsonObject }.orEmpty()
}

private fun String.clcplusFixtureRows(): List<JsonObject> {
    val root = json.parseToJsonElement(this).jsonObject
    return root["response"]?.jsonObject?.get("samples")?.jsonArray?.map { it.jsonObject }.orEmpty()
}

private fun String.singleClcplusValueOrNull(): String? {
    val root = json.parseToJsonElement(this).jsonObject
    return root.string("value")
}

private fun PlausibilityHttpResponse.toFailure(source: String): InstitutionalSourceFailure? = when (status) {
    in 200..299 -> null
    429 -> InstitutionalSourceFailure(source, InstitutionalSourceFailureReason.RATE_LIMITED, "The source rate-limited this query.")
    in 500..599 -> InstitutionalSourceFailure(source, InstitutionalSourceFailureReason.SERVICE_UNAVAILABLE, "The source responded with HTTP $status.")
    else -> InstitutionalSourceFailure(source, InstitutionalSourceFailureReason.UNEXPECTED_RESPONSE, "The source responded with HTTP $status.")
}

private fun networkFailure(source: String): InstitutionalSourceFailure = InstitutionalSourceFailure(
    source,
    InstitutionalSourceFailureReason.NETWORK,
    "The source could not be reached.",
)

private fun malformedFailure(source: String): InstitutionalSourceFailure = InstitutionalSourceFailure(
    source,
    InstitutionalSourceFailureReason.MALFORMED_RESPONSE,
    "The source response was invalid.",
)

private fun GeoBounds.describe(): String = "$west,$south,$east,$north"
private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
private val json = Json { ignoreUnknownKeys = true }
