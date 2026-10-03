package it.faunavia.exploration

import it.faunavia.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class ProfileField(val label: String) {
    DESCRIPTION("Descrizione"), HABITAT("Habitat generale"), SEASON("Stagionalità generale"), SIZE("Dimensioni"),
    DIET("Alimentazione"), BEHAVIOR("Comportamento"), CONSERVATION("Conservazione"), SAFETY("Osservare con rispetto"),
}

data class ProfileFact(val text: String, val source: Provenance) {
    init { require(text.isNotBlank() && text.length <= 16_000) }
}
data class SpeciesCuriosity(val title: String, val fact: ProfileFact) {
    init { require(title.isNotBlank() && title.length <= 160) }
}

/** Natural history is presentation data. It never enters the local evidence/ranking engine. */
data class ReadableSpeciesProfile(
    val scientificName: String,
    val commonName: String?,
    val nameSource: Provenance?,
    val facts: Map<ProfileField, ProfileFact>,
    val curiosities: List<SpeciesCuriosity> = emptyList(),
    val contentVersion: String,
    val schemaVersion: Int = 1,
) {
    init {
        require(scientificName.isNotBlank() && scientificName.length <= 500)
        require(commonName == null || commonName.isNotBlank() && commonName.length <= 500)
        require(schemaVersion == 1 && contentVersion.isNotBlank() && contentVersion.length <= 500 && curiosities.size <= 20)
    }
}

data class SpeciesProfileResult(val profile: ReadableSpeciesProfile, val warning: String? = null, val cached: Boolean = false)
fun interface SpeciesProfileLookup { suspend fun read(id: String, scientificName: String, commonName: String?): SpeciesProfileResult }

/** Bounded normalized cache, separate from selected taxa, diary and backup data. */
interface SpeciesProfileCache {
    fun read(key: String): String?
    fun save(key: String, value: String)
}
private object NoProfileCache : SpeciesProfileCache {
    override fun read(key: String): String? = null
    override fun save(key: String, value: String) = Unit
}

class SpeciesProfileService(
    private val catalogue: CatalogueRepository? = null,
    private val cache: SpeciesProfileCache = NoProfileCache,
    private val clock: () -> Instant = Instant::now,
) : SpeciesProfileLookup {
    override suspend fun read(id: String, scientificName: String, commonName: String?): SpeciesProfileResult {
        require(id.isNotBlank() && scientificName.isNotBlank())
        val key = "profile-v1:$id:${scientificIdentity(scientificName)}"
        val warnings = mutableListOf<String>()
        var localFailed = false
        var taxon: Taxon? = null
        var local: SpeciesProfile? = null
        try {
            taxon = catalogue?.taxon(id)?.takeIf { scientificIdentity(it.scientificName) == scientificIdentity(scientificName) }
            local = taxon?.let { catalogue?.profile(id) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { localFailed = true; warnings += "Scheda locale non leggibile. Riprova." }
        val curated = PilotNaturalHistory.find(scientificName)
        val fresh = local?.let { stored -> legacyProfile(scientificName, taxon, stored).let { profile ->
            if (curated == null) profile else profile.copy(facts = curated.facts + profile.facts,
                curiosities = curated.curiosities, contentVersion = "${profile.contentVersion}+${curated.contentVersion}")
        } } ?: curated
        var cached = false
        val saved = if (fresh == null && localFailed) try {
            cache.read(key)?.let { SpeciesProfileCodec.decode(it, scientificName) }?.also { cached = true }
        } catch (_: Exception) { warnings += "Cache della scheda non leggibile; mostro i dati disponibili."; null } else null
        val profile = fresh ?: saved ?: ReadableSpeciesProfile(scientificName, commonName?.takeIf(String::isNotBlank),
            taxon?.provenance, emptyMap(), contentVersion = "unavailable-v1")
        if (cached) warnings += "Copia offline precedente: la scheda locale non è stata verificata."
        if (!cached && fresh != null) try { cache.save(key, SpeciesProfileCodec.encode(profile, clock()))
        } catch (_: Exception) { warnings += "Non riesco a conservare la copia offline. Riprova." }
        return SpeciesProfileResult(profile, warnings.takeIf { it.isNotEmpty() }?.joinToString(" "), cached)
    }
}

private fun legacyProfile(name: String, taxon: Taxon?, value: SpeciesProfile): ReadableSpeciesProfile {
    val values = mapOf(ProfileField.DESCRIPTION to value.description, ProfileField.HABITAT to value.habitats.joinToString(),
        ProfileField.SEASON to value.activeMonths.sorted().joinToString { Month.of(it).getDisplayName(TextStyle.FULL, Locale.ITALIAN) },
        ProfileField.SIZE to value.size, ProfileField.DIET to value.diet, ProfileField.BEHAVIOR to value.behavior,
        ProfileField.CONSERVATION to value.conservation)
    return ReadableSpeciesProfile(name, taxon?.commonName, taxon?.provenance,
        values.mapNotNull { (field, text) -> text?.takeIf(String::isNotBlank)?.let { field to ProfileFact(it, value.provenance) } }.toMap(),
        contentVersion = "local:${value.provenance.version}")
}

object SpeciesProfileCodec {
    const val MAX_BYTES = 128 * 1024
    fun encode(value: ReadableSpeciesProfile, cachedAt: Instant): String = buildJsonObject {
        put("schemaVersion", value.schemaVersion); put("contentVersion", value.contentVersion); put("scientificName", value.scientificName)
        value.commonName?.let { put("commonName", it) }; value.nameSource?.let { put("nameSource", sourceJson(it)) }
        put("cachedAt", cachedAt.toString())
        put("facts", buildJsonObject { value.facts.forEach { (field, fact) -> put(field.name, factJson(fact)) } })
        put("curiosities", buildJsonArray { value.curiosities.forEach { add(buildJsonObject { put("title", it.title); put("fact", factJson(it.fact)) }) } })
    }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }

    fun decode(raw: String, scientificName: String): ReadableSpeciesProfile {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = Json.parseToJsonElement(raw).jsonObject
        fun text(key: String) = root[key]?.jsonPrimitive?.content
        Instant.parse(text("cachedAt"))
        require(root.getValue("schemaVersion").jsonPrimitive.int == 1)
        val name = requireNotNull(text("scientificName"))
        require(scientificIdentity(name) == scientificIdentity(scientificName))
        val facts = root.getValue("facts").jsonObject.map { (key, value) -> ProfileField.valueOf(key) to readFact(value.jsonObject) }.toMap()
        val curiosities = root.getValue("curiosities").jsonArray.map { value -> value.jsonObject.let {
            SpeciesCuriosity(it.getValue("title").jsonPrimitive.content, readFact(it.getValue("fact").jsonObject))
        } }
        return ReadableSpeciesProfile(name, text("commonName"), root["nameSource"]?.jsonObject?.let(::readSource), facts, curiosities,
            requireNotNull(text("contentVersion")))
    }
    private fun factJson(value: ProfileFact) = buildJsonObject { put("text", value.text); put("source", sourceJson(value.source)) }
    private fun readFact(value: JsonObject) = ProfileFact(value.getValue("text").jsonPrimitive.content, readSource(value.getValue("source").jsonObject))
}

/** No file, javascript, credentials or invented source link reaches ACTION_VIEW. */
fun safeProfileSource(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)
