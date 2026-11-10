package it.faunavia.exploration

import it.faunavia.domain.*
import it.faunavia.network.JsonHttpPolicy
import it.faunavia.network.JsonHttpResponse
import it.faunavia.network.JsonHttpTransport
import it.faunavia.route.RouteAnalysisConfig
import it.faunavia.route.RouteEngine
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class OutingSearchResult(val proposals: List<OutingProposal>, val warning: String? = null)
fun interface OutingSearch { suspend fun search(trip: Trip): OutingSearchResult }
fun interface OutingHttpClient { fun post(query: String): JsonHttpResponse }

class OverpassOutingHttpClient(private val endpoint: String = "https://overpass-api.de/api/interpreter") : OutingHttpClient {
    private val transport = JsonHttpTransport()
    override fun post(query: String) = transport.post(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"),
        JsonHttpPolicy(connectTimeoutMillis = 5_000, readTimeoutMillis = 30_000,
            userAgent = FAUNAVIA_HTTP_USER_AGENT, maxBodyBytes = 2_000_000))
}

/** Personal prototype, one bounded request per explicit action; no persistent catalogue or automatic retries. */
class OverpassOutingSearch(private val http: OutingHttpClient = OverpassOutingHttpClient(),
    private val now: () -> Instant = Instant::now) : OutingSearch {
    private var retryNotBefore: Instant? = null
    companion object {
        private val requests = Mutex()
        private var lastRequestNanos: Long? = null
        const val MAX_BOXES = 12
        const val MAX_ELEMENTS = 600
    }

    override suspend fun search(trip: Trip): OutingSearchResult = requests.withLock {
        try {
            currentCoroutineContext().ensureActive()
            if (retryNotBefore?.isAfter(now()) == true) return@withLock OutingSearchResult(emptyList(),
                "Il servizio ha richiesto una pausa. Riprova dopo ${retryNotBefore}; le uscite salvate restano disponibili.")
            val plan = outingQuery(trip)
            val wait = lastRequestNanos?.let { (3_000 - (System.nanoTime() - it) / 1_000_000).coerceAtLeast(0) } ?: 0
            if (wait > 0) kotlinx.coroutines.delay(wait)
            currentCoroutineContext().ensureActive()
            lastRequestNanos = System.nanoTime()
            val response = http.post(plan.query)
            currentCoroutineContext().ensureActive()
            if (response.status !in 200..299) {
                if (response.status == 429) {
                    val seconds = response.retryAfter?.toLongOrNull()?.coerceIn(3, 600) ?: 60
                    retryNotBefore = now().plusSeconds(seconds)
                    return@withLock OutingSearchResult(emptyList(),
                        "Il servizio è occupato. Attendi $seconds secondi e premi Riprova ricerca; le uscite salvate restano disponibili.")
                }
                return@withLock OutingSearchResult(emptyList(),
                    "Ricerca online non disponibile. Riprova o approfondisci su Google; le uscite salvate restano disponibili.")
            }
            val parsed = parseOutingSearch(response.body, plan.query, now())
            parsed.copy(warning = listOfNotNull(plan.warning, parsed.warning).joinToString(" ").ifBlank { null })
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            OutingSearchResult(emptyList(), "Ricerca online non riuscita. Controlla la connessione e riprova; le uscite salvate restano disponibili.")
        }
    }
}

internal data class OutingQuery(val query: String, val warning: String?)

/** Reuses F5's European corridor grid. Only aggregate 20 km boxes leave the device. */
internal fun outingQuery(trip: Trip): OutingQuery {
    val route = trip.route?.geometry ?: Route("outing-area", trip.destination.name, listOf(trip.destination.center), trip.updatedAt)
    val chunks = RouteEngine().analyze(route, RouteAnalysisConfig(corridorRadiusMeters = trip.radiusMeters,
        samplingIntervalMeters = 2_000.0, queryChunkCellsPerAxis = 20)).queryChunks
    val selected = if (chunks.size <= OverpassOutingSearch.MAX_BOXES) chunks else
        List(OverpassOutingSearch.MAX_BOXES) { index -> chunks[index * (chunks.size - 1) / (OverpassOutingSearch.MAX_BOXES - 1)] }
    val clauses = selected.joinToString("") { chunk ->
        val b = chunk.bounds
        val box = listOf(b.south, b.west, b.north, b.east).joinToString(",") { String.format(Locale.ROOT, "%.6f", it) }
        listOf("nwr[leisure=nature_reserve]", "nwr[boundary=national_park]", "nwr[leisure=bird_hide]",
            "rel[route=hiking]", "rel[route=foot]").joinToString("") { "$it($box);" }
    }
    return OutingQuery("[out:json][timeout:20][maxsize:16777216];($clauses);out meta center ${OverpassOutingSearch.MAX_ELEMENTS + 1};",
        if (chunks.size > selected.size) "Copertura parziale: ricercate ${selected.size} aree su ${chunks.size} lungo il percorso. Seleziona una tappa per una ricerca più completa." else null)
}

internal fun parseOutingSearch(body: String, query: String, retrievedAt: Instant): OutingSearchResult {
    val root = Json.parseToJsonElement(body).jsonObject
    val elements = requireNotNull(root["elements"] as? JsonArray)
    val databaseDate = (root["osm3s"] as? JsonObject)?.text("timestamp_osm_base") ?: "data database non specificata"
    var skipped = 0
    val proposals = elements.take(OverpassOutingSearch.MAX_ELEMENTS).mapNotNull { element ->
        try { parseOutingElement(element.jsonObject, query, retrievedAt, databaseDate) }
        catch (_: Exception) { skipped++; null }
    }.distinctBy { it.id }
    val warnings = mutableListOf<String>()
    if (root.text("remark") != null) warnings += "Risposta parziale del servizio: alcuni luoghi potrebbero mancare. Riprova ricerca."
    if (elements.size > OverpassOutingSearch.MAX_ELEMENTS) warnings += "Limite risultati raggiunto: restringi il raggio o seleziona una tappa."
    if (skipped > 0) warnings += "Alcuni record incompleti sono stati esclusi."
    return OutingSearchResult(proposals, warnings.joinToString(" ").ifBlank { null })
}

private fun parseOutingElement(record: JsonObject, query: String, at: Instant, databaseDate: String): OutingProposal? {
    val tags = record["tags"] as? JsonObject ?: return null
    if (tags.text("access") in listOf("private", "no") || tags.text("foot") in listOf("private", "no")) return null
    val trail = tags.text("route") in listOf("hiking", "foot")
    val kind = when {
        trail -> "Itinerario a piedi"
        tags.text("leisure") == "bird_hide" -> "Punto di osservazione"
        tags.text("leisure") == "nature_reserve" -> "Riserva naturale"
        tags.text("boundary") == "national_park" -> "Parco naturale"
        else -> return null
    }
    val type = requireNotNull(record.text("type")).also { require(it in listOf("node", "way", "relation")) }
    val number = requireNotNull(record["id"]?.jsonPrimitive?.longOrNull).also { require(it > 0) }
    val id = "osm:$type:$number"
    val coordinates = if (type == "node") record else record["center"] as? JsonObject ?: error("Missing center")
    val point = GeoPoint(requireNotNull(coordinates["lat"]?.jsonPrimitive?.doubleOrNull),
        requireNotNull(coordinates["lon"]?.jsonPrimitive?.doubleOrNull))
    val name = (tags.text("name:it") ?: tags.text("name") ?: tags.text("ref")?.let { "$kind $it" } ?: "$kind OSM $number").take(160)
    val version = "Overpass/OSM v${record.text("version") ?: "?"} · $databaseDate"
    val source = Provenance("https://www.openstreetmap.org/$type/$number", "$type/$number", query, at,
        "ODbL 1.0", "© OpenStreetMap contributors", "Mappatura collaborativa; accesso e precisione non verificati; punto rappresentativo", version)
    val website = safeOutingWebsite(tags.text("website") ?: tags.text("contact:website"))
    val access = buildList {
        add("$kind mappato in OpenStreetMap. Verifica accesso e chiusure con il gestore.")
        listOf("access" to "Accesso", "opening_hours" to "Orari", "fee" to "Ingresso a pagamento",
            "operator" to "Gestore", "seasonal" to "Stagionalità").forEach { (key, label) ->
            tags.text(key)?.let { add("$label (OSM): ${it.take(500)}.") }
        }
        website?.let { add("Sito indicato in OSM: $it") }
    }.joinToString(" ").take(4_096)
    val duration = tags.text("duration")?.let(::outingDurationMinutes)
    val length = tags.text("distance")?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        ?.let { (it * 1_000).toInt().takeIf { value -> value in 1..200_000 } }
    val guide = OutingGuide(id, version, at, "Punto rappresentativo di $name; verifica l’ingresso sul sito del gestore".take(512),
        if (trail) length else null, if (trail) duration else null, tags.text("sac_scale")?.let { "OSM sac_scale: ${it.take(100)}" },
        access, tags.text("wheelchair")?.let { "OSM wheelchair: ${it.take(100)}" }, emptyList(), source, source, source)
    return OutingProposal(id, name, TripPlace(name, point, if (trail) "OSM_WALKING_ROUTE" else "OSM_NATURE_PLACE", source), guide, website = website)
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

internal fun outingDurationMinutes(value: String): Int? {
    val parts = value.split(':')
    val minutes = when {
        parts.size == 1 -> parts[0].toIntOrNull()?.toLong()
        parts.size == 2 -> {
            val hours = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val remainder = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            hours * 60L + remainder
        }
        else -> null
    }
    return minutes?.takeIf { it in 1..10_080 }?.toInt()
}

internal fun safeOutingWebsite(value: String?): String? = value?.takeIf { it.length <= 1_024 }?.let {
    val uri = runCatching { URI(it) }.getOrNull() ?: return@let null
    it.takeIf { uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null }
}

/** Only public place labels are sent; no stored geometry, wishlist or diary content. */
fun outingWebSearchUrl(trip: Trip, proposal: OutingProposal? = null): String {
    val labels = proposal?.name ?: listOfNotNull(trip.departure?.name, trip.destination.name).joinToString(" ")
    val query = "$labels sentieri riserve naturali birdwatching accesso"
    return "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
}

val PersonalOutingSearch: OutingSearch = OverpassOutingSearch()
