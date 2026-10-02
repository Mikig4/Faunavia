package it.faunavia.exploration

import it.faunavia.domain.AppClock
import it.faunavia.domain.Provenance
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

data class SpeciesNameMetadata(val commonName: String?, val gbifKey: String?, val source: Provenance?)
data class RangeIllustration(val imageUrl: String, val date: String, val source: Provenance)
data class SpeciesDistribution(val illustration: RangeIllustration?, val gbifKey: String?, val source: Provenance)
data class MetadataResult<T>(val value: T?, val warning: String? = null, val stale: Boolean = false)

interface SpeciesMetadataLookup {
    suspend fun name(id: String, scientificName: String, refresh: Boolean = false): MetadataResult<SpeciesNameMetadata>
    suspend fun distribution(id: String, scientificName: String, refresh: Boolean = false): MetadataResult<SpeciesDistribution>
}

object UnavailableSpeciesMetadata : SpeciesMetadataLookup {
    override suspend fun name(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesNameMetadata>(null)
    override suspend fun distribution(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesDistribution>(null,
        "Dati cartografici non disponibili. Riprova con la rete disponibile.")
}

interface SpeciesMetadataCache {
    fun read(key: String): String?
    fun save(key: String, normalized: String)
}

interface SpeciesMetadataHttp { fun get(url: String): String }

class UrlConnectionSpeciesMetadataHttp : SpeciesMetadataHttp {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", FAUNAVIA_HTTP_USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) throw IOException("Species metadata HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var count = input.read(buffer)
                while (count >= 0) {
                    if (output.size() + count > 1_048_576) throw IOException("Species metadata response too large")
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
}

/** Presentation only: never selects a diary taxon or contributes range evidence to plausibility. */
class RemoteSpeciesMetadata(private val http: SpeciesMetadataHttp, private val cache: SpeciesMetadataCache,
    private val clock: AppClock) : SpeciesMetadataLookup {
    private val json = Json { ignoreUnknownKeys = true }
    private val requests = Semaphore(2)
    private val gbif = "https://api.gbif.org/v1/species"
    private val commons = "https://commons.wikimedia.org/w/api.php"
    private val wikidata = "https://www.wikidata.org/w/api.php"

    override suspend fun name(id: String, scientificName: String, refresh: Boolean): MetadataResult<SpeciesNameMetadata> = requests.withPermit {
        val key = "name-v1:$id:${scientificIdentity(scientificName)}"
        val cached = cached(key)
        val cachedName = cached?.let { runCatching { readName(it) }.getOrNull() }
        if (!refresh && fresh(cached) && cachedName != null) return@withPermit MetadataResult(cachedName)
        try {
            val result = lookupName(id, scientificName)
            cache.save(key, buildJsonObject {
                put("cachedAt", now().toString()); put("commonName", result.commonName); put("gbifKey", result.gbifKey)
                result.source?.let { put("source", sourceJson(it)) }
            }.toString())
            MetadataResult(result)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { MetadataResult(cachedName, "Nome comune non recuperabile. Riprova con la rete disponibile.", cachedName != null) }
    }

    override suspend fun distribution(id: String, scientificName: String, refresh: Boolean): MetadataResult<SpeciesDistribution> {
        val key = "map-v1:$id:${scientificIdentity(scientificName)}"
        val cached = cached(key)
        val cachedMap = cached?.let { runCatching { readDistribution(it) }.getOrNull() }
        if (!refresh && fresh(cached) && cachedMap != null) return MetadataResult(cachedMap)
        val identity = name(id, scientificName)
        return requests.withPermit {
            val gbifKey = identity.value?.gbifKey ?: id.removePrefix("gbif:").takeIf { id.startsWith("gbif:") && it.matches(Regex("[0-9]+")) }
            val mapSource = source("https://techdocs.gbif.org/en/openapi/v2/maps", gbifKey ?: scientificName,
                "Distribuzione delle segnalazioni archiviate: $scientificName", "GBIF Maps API; licenze dei dataset originali applicabili",
                "GBIF", "segnalazioni storiche aggregate; copertura incompleta, non probabilità di incontro", "GBIF Maps v2")
            try {
                val file = PilotSpeciesPresentation.find(scientificName)?.distribution?.source?.recordId
                    ?: rangeFile(scientificName)
                val illustration = file?.let { commonsIllustration(it, scientificName) }
                val value = SpeciesDistribution(illustration, gbifKey, mapSource)
                cache.save(key, buildJsonObject {
                    put("cachedAt", now().toString()); put("gbifKey", gbifKey); put("source", sourceJson(mapSource))
                    illustration?.let { image -> put("illustration", buildJsonObject {
                        put("imageUrl", image.imageUrl); put("date", image.date); put("source", sourceJson(image.source))
                    }) }
                }.toString())
                MetadataResult(value, if (illustration == null) "Mappa di areale illustrata non disponibile: consulta le segnalazioni GBIF." else null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                MetadataResult(cachedMap ?: SpeciesDistribution(null, gbifKey, mapSource),
                    "Mappa di areale non recuperabile. Le segnalazioni GBIF restano accessibili se disponibili; puoi riprovare.", cachedMap != null)
            }
        }
    }

    private fun lookupName(id: String, name: String): SpeciesNameMetadata {
        val suppliedKey = id.removePrefix("gbif:").takeIf { id.startsWith("gbif:") && it.matches(Regex("[0-9]+")) }
        val supplied = suppliedKey?.let { objectAt("$gbif/$it") }
        val record = supplied?.takeIf { exactAnimal(it, name) } ?: objectAt("$gbif/match?name=${encoded(name)}&kingdom=Animalia&strict=true")
            .takeIf { it.text("matchType") == "EXACT" && exactAnimal(it, name) }
            ?: return SpeciesNameMetadata(null, null, null)
        val taxonKey = record.text("key") ?: record.text("usageKey") ?: return SpeciesNameMetadata(null, null, null)
        val names = objectAt("$gbif/$taxonKey/vernacularNames?limit=100")["results"]?.jsonArray.orEmpty()
        val common = names.map { it.jsonObject }.firstOrNull { it.text("language") in listOf("ita", "it") && !it.text("vernacularName").isNullOrBlank() }
            ?.text("vernacularName")?.trim()
        return SpeciesNameMetadata(common, taxonKey, source("$gbif/$taxonKey/vernacularNames", taxonKey,
            "Italian common name, exact scientific identity: $name", "GBIF API terms; downstream dataset licenses applicable",
            "GBIF Backbone Taxonomy", "identità esatta Animalia; nome comune italiano, quando presente", "GBIF Species v1"))
    }

    private fun exactAnimal(record: JsonObject, name: String): Boolean = record.text("kingdom") == "Animalia" &&
        (record.text("taxonomicStatus") ?: record.text("status")) == "ACCEPTED" &&
        record.text("rank") in listOf("SPECIES", "SUBSPECIES") &&
        scientificIdentity(record.text("canonicalName") ?: record.text("scientificName").orEmpty()) == scientificIdentity(name)

    private fun rangeFile(name: String): String? {
        val identity = scientificIdentity(name)
        val search = objectAt("$wikidata?action=wbsearchentities&search=${encoded(identity)}&language=en&type=item&limit=5&format=json")
        val ids = search["search"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.text("id") }.filter { it.matches(Regex("Q[0-9]+")) }
        if (ids.isEmpty()) return null
        val entities = objectAt("$wikidata?action=wbgetentities&ids=${encoded(ids.joinToString("|"))}&props=claims&format=json")["entities"]?.jsonObject ?: return null
        val exact = entities.values.map { it.jsonObject["claims"]?.jsonObject ?: JsonObject(emptyMap()) }
            .filter { claims -> claimValues(claims, "P225").any { scientificIdentity(it) == identity } }
        if (exact.size != 1) return null
        return claimValues(exact.single(), "P181").firstOrNull()
    }

    private fun claimValues(claims: JsonObject, property: String): List<String> = claims[property]?.jsonArray.orEmpty()
        .map { it.jsonObject }.filter { it.text("rank") != "deprecated" }.sortedBy { if (it.text("rank") == "preferred") 0 else 1 }
        .mapNotNull { it["mainsnak"]?.jsonObject?.get("datavalue")?.jsonObject?.text("value") }

    private fun commonsIllustration(file: String, name: String): RangeIllustration? {
        val response = objectAt("$commons?action=query&titles=${encoded("File:$file")}&prop=imageinfo&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=1600&format=json")
        val pages = response["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        val info = pages.values.firstOrNull()?.jsonObject?.get("imageinfo")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val metadata = info["extmetadata"]?.jsonObject ?: return null
        fun field(key: String) = metadata[key]?.jsonObject?.text("value")?.let(::plainText)
        val license = field("LicenseShortName") ?: return null
        if (!reusableMapLicense(license)) return null
        val author = field("Artist")?.takeIf(String::isNotBlank) ?: return null
        val imageUrl = info.text("thumburl") ?: info.text("url") ?: return null
        if (!safeCommonsMedia(imageUrl)) return null
        val mime = info.text("thumbmime") ?: info.text("mime")
        if (mime !in listOf("image/png", "image/jpeg", "image/gif", "image/webp")) return null
        val description = info.text("descriptionurl") ?: return null
        if (URI(description).host != "commons.wikimedia.org" || URI(description).scheme != "https") return null
        val date = field("DateTimeOriginal") ?: field("DateTime") ?: "Data della mappa non specificata"
        return RangeIllustration(imageUrl, date, source(description, file, "Distribution illustration: $name",
            license, "$author · Wikimedia Commons", "illustrazione generale datata; legenda nell'immagine, non presenza attuale né verifica locale", "Commons imageinfo + Wikidata P225/P181"))
    }

    private fun cached(key: String): JsonObject? = runCatching { cache.read(key)?.let { json.parseToJsonElement(it).jsonObject } }.getOrNull()
    private fun fresh(value: JsonObject?): Boolean = value?.text("cachedAt")?.let {
        runCatching { Duration.between(Instant.parse(it), now()).let { age -> !age.isNegative && age < Duration.ofDays(30) } }.getOrDefault(false)
    } ?: false
    private fun readName(value: JsonObject) = SpeciesNameMetadata(value.text("commonName"), value.text("gbifKey"), value["source"]?.jsonObject?.let(::readSource))
    private fun readDistribution(value: JsonObject) = SpeciesDistribution(value["illustration"]?.jsonObject?.let {
        RangeIllustration(requireNotNull(it.text("imageUrl")), requireNotNull(it.text("date")), readSource(it.getValue("source").jsonObject))
    }, value.text("gbifKey"), readSource(value.getValue("source").jsonObject))
    private fun objectAt(url: String) = json.parseToJsonElement(http.get(url)).jsonObject
    private fun now() = Instant.ofEpochMilli(clock.nowEpochMillis())
    private fun source(url: String, record: String, query: String, license: String, attribution: String, quality: String, version: String) =
        Provenance(url, record, query, now(), license, attribution, quality, version)
}

private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
private fun plainText(value: String) = value.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"")
    .replace("&#39;", "'").replace(Regex("\\s+"), " ").trim()
fun reusableMapLicense(value: String): Boolean = value in setOf("Public domain", "CC0", "CC0 1.0") || value.matches(Regex("CC BY(?:-SA)? (?:1\\.0|2\\.0|2\\.5|3\\.0|4\\.0)"))
fun safeCommonsMedia(value: String): Boolean = runCatching { URI(value).let {
    it.scheme == "https" && it.host in setOf("upload.wikimedia.org", "thumb.wikimedia.org") && it.userInfo == null
} }.getOrDefault(false)
fun gbifDistributionTiles(key: String): String {
    require(key.matches(Regex("[0-9]+")))
    return "https://api.gbif.org/v2/map/occurrence/density/{z}/{x}/{y}@1x.png?srs=EPSG:3857&taxonKey=$key&hasGeospatialIssue=false&basisOfRecord=HUMAN_OBSERVATION&basisOfRecord=MACHINE_OBSERVATION&bin=hex&hexPerTile=57&style=green.poly"
}
private fun sourceJson(value: Provenance) = buildJsonObject {
    put("source", value.source); put("recordId", value.recordId); put("query", value.query); put("retrievedAt", value.retrievedAt.toString())
    put("license", value.license); put("attribution", value.attribution); put("quality", value.quality); put("version", value.version)
}
private fun readSource(value: JsonObject) = Provenance(requireNotNull(value.text("source")), requireNotNull(value.text("recordId")), requireNotNull(value.text("query")),
    Instant.parse(value.text("retrievedAt")), requireNotNull(value.text("license")), requireNotNull(value.text("attribution")), requireNotNull(value.text("quality")), requireNotNull(value.text("version")))
