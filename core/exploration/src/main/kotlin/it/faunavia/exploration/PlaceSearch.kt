package it.faunavia.exploration

import it.faunavia.domain.GeoPoint
import it.faunavia.route.GeoBounds
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PlaceKind { COUNTRY, REGION, CITY, OTHER }

const val FAUNAVIA_HTTP_USER_AGENT = "Faunavia/0.8 (+https://github.com/Mikig4/Faunavia)"

data class PlaceCandidate(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val countryCode: String?,
    val center: GeoPoint,
    val bounds: GeoBounds?,
) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

enum class PlaceOrigin { NETWORK, CACHE }

sealed interface PlaceSearchResult {
    data class Matches(val places: List<PlaceCandidate>, val origin: PlaceOrigin) : PlaceSearchResult
    data class Unavailable(val cachedPlaces: List<PlaceCandidate> = emptyList()) : PlaceSearchResult
}

fun interface PlaceSearch {
    /** Called only on explicit user submission, never on every keystroke. */
    suspend fun search(query: String): PlaceSearchResult
}

interface PlaceCache {
    fun read(query: String): List<PlaceCandidate>?
    fun write(query: String, places: List<PlaceCandidate>)
}

data class PlaceHttpResponse(val status: Int, val body: String)

fun interface PlaceHttpClient {
    fun get(url: String): PlaceHttpResponse
}

class UrlConnectionPlaceHttpClient : PlaceHttpClient {
    override fun get(url: String): PlaceHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", FAUNAVIA_HTTP_USER_AGENT)
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return PlaceHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}

/** Public Nominatim is user-triggered only, cached and capped at one request per second. */
class NominatimPlaceSearch(
    private val http: PlaceHttpClient,
    private val cache: PlaceCache,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val waitMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : PlaceSearch {
    private var lastNetworkRequestAt = Long.MIN_VALUE
    private val requestMutex = Mutex()

    override suspend fun search(query: String): PlaceSearchResult = requestMutex.withLock {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.length !in 3..160) return@withLock PlaceSearchResult.Matches(emptyList(), PlaceOrigin.CACHE)
        cache.read(normalized)?.let { return@withLock PlaceSearchResult.Matches(it, PlaceOrigin.CACHE) }
        val remaining = if (lastNetworkRequestAt == Long.MIN_VALUE) 0L else
            (1_000L - (nowMillis() - lastNetworkRequestAt)).coerceAtLeast(0L)
        if (remaining > 0L) waitMillis(remaining)
        lastNetworkRequestAt = nowMillis()
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8)
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&addressdetails=1&limit=5"
        val response = runCatching { http.get(url) }.getOrNull()
            ?: return@withLock PlaceSearchResult.Unavailable(cache.read(normalized).orEmpty())
        if (response.status !in 200..299) return@withLock PlaceSearchResult.Unavailable(cache.read(normalized).orEmpty())
        val places = runCatching { parsePlaces(response.body) }.getOrNull()
            ?: return@withLock PlaceSearchResult.Unavailable(cache.read(normalized).orEmpty())
        cache.write(normalized, places)
        PlaceSearchResult.Matches(places, PlaceOrigin.NETWORK)
    }
}

internal fun parsePlaces(body: String): List<PlaceCandidate> = Json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
    val entry = element.jsonObject
    val id = entry["place_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
    val name = entry["display_name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
    val latitude = entry["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
    val longitude = entry["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
    val center = runCatching { GeoPoint(latitude, longitude) }.getOrNull() ?: return@mapNotNull null
    val address = entry["address"] as? JsonObject
    val kindText = entry["addresstype"]?.jsonPrimitive?.contentOrNull
        ?: entry["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val kind = when (kindText.lowercase(Locale.ROOT)) {
        "country" -> PlaceKind.COUNTRY
        "state", "region", "province", "county" -> PlaceKind.REGION
        "city", "town", "village", "municipality" -> PlaceKind.CITY
        else -> PlaceKind.OTHER
    }
    val bounds = (entry["boundingbox"] as? JsonArray)?.takeIf { it.size == 4 }?.let { values ->
        runCatching {
            GeoBounds(
                west = values[2].jsonPrimitive.doubleOrNull!!,
                south = values[0].jsonPrimitive.doubleOrNull!!,
                east = values[3].jsonPrimitive.doubleOrNull!!,
                north = values[1].jsonPrimitive.doubleOrNull!!,
            )
        }.getOrNull()
    }
    PlaceCandidate(id, name, kind, address?.get("country_code")?.jsonPrimitive?.contentOrNull, center, bounds)
}
