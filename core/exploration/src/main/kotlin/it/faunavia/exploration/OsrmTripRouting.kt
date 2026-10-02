package it.faunavia.exploration

import it.faunavia.network.JsonHttpPolicy
import it.faunavia.network.JsonHttpTransport

import it.faunavia.domain.*
import it.faunavia.route.RouteEngine
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

fun interface RoutingHttpClient {
    fun get(url: String): String
}

class UrlConnectionRoutingHttpClient : RoutingHttpClient {
    private val transport = JsonHttpTransport()
    private val policy = JsonHttpPolicy(readTimeoutMillis = 15_000, userAgent = FAUNAVIA_HTTP_USER_AGENT,
        maxBodyChars = 5_000_000, bodyTooLargeMessage = "Routing response too large")

    override fun get(url: String): String = transport.get(url, policy) { status ->
        if (status in 200..299) null else IOException("Routing unavailable")
    }.body
}

/** Public OSRM demo: personal prototype only, at most one request/second and no auto retries. */
class OsrmTripRouting(
    private val http: RoutingHttpClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val waitMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val endpoint: String = "https://router.project-osrm.org/route/v1/driving",
) : TripRouting {
    private val mutex = Mutex()
    private var lastRequestAt: Long? = null
    private val cache = linkedMapOf<String, Pair<Long, List<TripRoute>>>()

    override suspend fun plan(departure: TripPlace, destination: TripPlace): TripRoutingResult = mutex.withLock {
        if (departure.center == destination.center) return@withLock TripRoutingResult.NoRoute
        val coordinates = listOf(departure.center, destination.center).joinToString(";") { "${it.longitude},${it.latitude}" }
        val key = "$coordinates|${departure.name}|${destination.name}"
        cache[key]?.takeIf { nowMillis() - it.first < 600_000 }?.let {
            return@withLock TripRoutingResult.Routes(it.second)
        }
        val remaining = lastRequestAt?.let { (1_000 - (nowMillis() - it)).coerceAtLeast(0) } ?: 0
        if (remaining > 0) waitMillis(remaining)
        lastRequestAt = nowMillis()
        val request = "$endpoint/$coordinates?alternatives=true&geometries=geojson&overview=full&steps=false&generate_hints=false"
        val result = try { parse(http.get(request), departure, destination, request) }
        catch (_: IOException) { TripRoutingResult.Unavailable }
        catch (_: IllegalArgumentException) { TripRoutingResult.Unavailable }
        catch (_: kotlinx.serialization.SerializationException) { TripRoutingResult.Unavailable }
        if (result is TripRoutingResult.Routes) {
            if (cache.size >= 20) cache.remove(cache.keys.first())
            cache[key] = nowMillis() to result.choices
        }
        result
    }

    private fun parse(body: String, departure: TripPlace, destination: TripPlace, request: String): TripRoutingResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.content
        if (code in listOf("NoRoute", "NoSegment")) return TripRoutingResult.NoRoute
        require(code == "Ok")
        val retrieved = Instant.ofEpochMilli(nowMillis())
        val choices = requireNotNull(root["routes"]).jsonArray.take(3).map { element ->
            val record = element.jsonObject
            val geometry = requireNotNull(record["geometry"]).jsonObject
            require(geometry["type"]?.jsonPrimitive?.content == "LineString")
            val coordinates = requireNotNull(geometry["coordinates"]).jsonArray
            require(coordinates.size in 2..10_000)
            val points = coordinates.map { point -> point.jsonArray.let {
                require(it.size >= 2)
                GeoPoint(it[1].jsonPrimitive.double, it[0].jsonPrimitive.double)
            } }
            val provisional = Route("unpersisted", "${departure.name} → ${destination.name}".take(160), points,
                retrieved, RouteSource.PLANNED, sourceName = "OSRM driving")
            val route = provisional.copy(id = "planned-${RouteEngine().geometryFingerprint(provisional).take(24)}")
            TripRoute(route, requireNotNull(record["distance"]).jsonPrimitive.double,
                requireNotNull(record["duration"]).jsonPrimitive.double,
                Provenance("OSRM / OpenStreetMap", route.id, request, retrieved, "ODbL",
                    "© OpenStreetMap contributors · OSRM", "calculated-driving-route; no live traffic",
                    root["data_version"]?.jsonPrimitive?.contentOrNull ?: "OSRM Route API v1; map date not supplied"))
        }.distinctBy { it.geometry.id }
        require(choices.isNotEmpty())
        return TripRoutingResult.Routes(choices)
    }
}
