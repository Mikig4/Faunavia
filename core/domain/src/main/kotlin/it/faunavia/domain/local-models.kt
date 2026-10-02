package it.faunavia.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Provenance(
    val source: String,
    val recordId: String,
    val query: String,
    val retrievedAt: Instant,
    val license: String,
    val attribution: String,
    val quality: String,
    val version: String,
) {
    init {
        require(listOf(source, recordId, query, license, attribution, quality, version).all { it.isNotBlank() })
    }
}

enum class TaxonomicStatus { ACCEPTED, SYNONYM, DOUBTFUL }

data class Taxon(
    val id: String,
    val scientificName: String,
    val commonName: String?,
    val kingdom: String,
    val status: TaxonomicStatus,
    val rank: String,
    val provenance: Provenance,
) {
    init { require(id.isNotBlank() && scientificName.isNotBlank() && kingdom.isNotBlank() && rank.isNotBlank()) }
    val isSelectable: Boolean get() = kingdom == "Animalia" && status == TaxonomicStatus.ACCEPTED
}

/** A common, scientific or historical name that resolves to the accepted [taxonId]. */
data class TaxonAlias(
    val taxonId: String,
    val name: String,
) {
    init { require(taxonId.isNotBlank() && name.isNotBlank()) }
}

data class TaxonPreview(val taxonId: String, val imageUri: String, val provenance: Provenance) {
    init { require(taxonId.isNotBlank() && imageUri.isNotBlank()) }
}

data class SpeciesProfile(
    val taxonId: String,
    val description: String,
    val habitats: List<String>,
    val activeMonths: Set<Int>,
    val diet: String?,
    val size: String?,
    val behavior: String?,
    val conservation: String?,
    val provenance: Provenance,
) {
    init { require(taxonId.isNotBlank() && activeMonths.all { it in 1..12 }) }
}

data class SuggestionProfile(
    val taxonId: String,
    val area: String,
    val habitats: List<String>,
    val urbanCommon: Boolean,
    val distinctivenessScore: Double,
    val reason: String,
    val provenance: Provenance,
    val schemaVersion: Int = 1,
) {
    init {
        require(taxonId.isNotBlank() && area.isNotBlank() && reason.isNotBlank())
        // Version 0 is read-only legacy data: absence of habitat must not be invented on migration.
        require(schemaVersion == 0 || (habitats.isNotEmpty() && habitats.all(String::isNotBlank)))
        require(distinctivenessScore in 0.0..1.0)
        require(schemaVersion in 0..1) { "Unsupported suggestion profile version." }
    }
}

data class WishlistEntry(val taxonId: String, val addedAt: Instant) {
    init { require(taxonId.isNotBlank()) }
}

/** Only explicitly selected accepted Animalia identities may enter the personal wishlist. */
interface WishlistRepository {
    suspend fun add(taxonId: String)
    suspend fun remove(taxonId: String)
    suspend fun list(): List<WishlistEntry>
}

data class Observation(
    val id: String,
    val taxonId: String,
    val observedAt: Instant,
    val zoneId: ZoneId,
    val location: GeoPoint?,
    val notes: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val quantity: Int = 1,
    val tripId: String? = null,
    val outingId: String? = null,
) {
    init {
        require(id.isNotBlank() && taxonId.isNotBlank())
        require(!updatedAt.isBefore(createdAt))
        require(quantity in 1..MAX_OBSERVATION_QUANTITY)
        require(notes.length <= MAX_OBSERVATION_NOTES_LENGTH)
        validateMemoryLink(tripId, outingId)
    }
    val localDate: LocalDate get() = observedAt.atZone(zoneId).toLocalDate()
}

/** Private normalized image metadata. observationId is the stable memory ID, including before identification. */
data class ObservationPhoto(
    val id: String,
    val observationId: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 1,
    val thumbnailPath: String? = null,
) {
    init {
        require(id.isNotBlank() && observationId.isNotBlank())
        require(relativePath.isNotBlank() && !relativePath.startsWith('/') && ':' !in relativePath && '\\' !in relativePath)
        require(relativePath.split('/').none { it == ".." || it == "." || it.isEmpty() })
        require(sha256.matches(Regex("[a-f0-9]{64}")) && byteSize > 0 && mimeType.startsWith("image/"))
        require(width >= 0 && height >= 0 && orientation in 1..8)
        thumbnailPath?.let {
            require(it.isNotBlank() && !it.startsWith('/') && ':' !in it && '\\' !in it)
            require(it.split('/').none { part -> part == ".." || part == "." || part.isEmpty() })
        }
    }
}

enum class RouteSource { LOCATION, GPX, GEOJSON, PLANNED, LEGACY }

data class Route(
    val id: String,
    val name: String,
    val points: List<GeoPoint>,
    val importedAt: Instant,
    val source: RouteSource = RouteSource.LEGACY,
    val segments: List<List<GeoPoint>> = listOf(points),
    val sourceName: String? = null,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && points.isNotEmpty())
        require(segments.isNotEmpty() && segments.all { it.isNotEmpty() })
        require(points == segments.flatten()) { "Route points must preserve every segment in order." }
        require(sourceName == null || sourceName.isNotBlank())
    }
}

data class SourceEvidence(
    val id: String,
    val taxonId: String,
    val level: EvidenceLevel,
    val eventAt: Instant?,
    val location: GeoPoint?,
    val uncertaintyMeters: Double?,
    val explanation: String,
    val provenance: Provenance,
) {
    init {
        require(id.isNotBlank() && taxonId.isNotBlank() && explanation.isNotBlank())
        require(uncertaintyMeters == null || (uncertaintyMeters.isFinite() && uncertaintyMeters >= 0))
    }
}

data class AppSettings(
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(20, 0),
    val zoneId: ZoneId = ZoneId.of("UTC"),
) {
    init { require(reminderTime.second == 0 && reminderTime.nano == 0) }
}

data class ObservationDraft(
    val id: String,
    val taxonId: String,
    val observedAt: Instant,
    val zoneId: ZoneId,
    val location: GeoPoint? = null,
    val notes: String = "",
    val quantity: Int = 1,
    val tripId: String? = null,
    val outingId: String? = null,
) {
    init {
        require(id.isNotBlank() && taxonId.isNotBlank())
        require(quantity in 1..MAX_OBSERVATION_QUANTITY)
        require(notes.length <= MAX_OBSERVATION_NOTES_LENGTH)
        validateMemoryLink(tripId, outingId)
    }
}

const val MAX_OBSERVATION_QUANTITY = 9_999
const val MAX_OBSERVATION_NOTES_LENGTH = 2_000

/** Missing reads return null/empty; invalid references and duplicate creates fail without writes. */
interface DiaryRepository {
    suspend fun create(draft: ObservationDraft, photos: List<ObservationPhoto> = emptyList()): Observation
    suspend fun update(draft: ObservationDraft): Observation
    suspend fun get(id: String): Observation?
    suspend fun list(): List<Observation>
    suspend fun onDate(date: LocalDate, zoneId: ZoneId): List<Observation>
    suspend fun photos(observationId: String): List<ObservationPhoto>
    suspend fun addPhoto(photo: ObservationPhoto)
    suspend fun deletePhoto(id: String): ObservationPhoto?
    suspend fun delete(id: String): List<ObservationPhoto>
}

interface CatalogueRepository {
    suspend fun saveTaxon(taxon: Taxon)
    /** Saves the accepted identity and all searchable names in one transaction. */
    suspend fun saveTaxonWithAliases(taxon: Taxon, aliases: List<String>)
    suspend fun taxon(id: String): Taxon?
    suspend fun aliases(taxonId: String): List<TaxonAlias>
    suspend fun searchTaxa(query: String, limit: Int): List<Taxon>
    suspend fun deleteTaxon(id: String)
    suspend fun savePreview(preview: TaxonPreview)
    suspend fun preview(taxonId: String): TaxonPreview?
    suspend fun saveProfile(profile: SpeciesProfile)
    suspend fun profile(taxonId: String): SpeciesProfile?
    suspend fun saveSuggestion(profile: SuggestionProfile)
    suspend fun suggestion(taxonId: String, area: String): SuggestionProfile?
    suspend fun saveEvidence(evidence: SourceEvidence)
    suspend fun evidence(taxonId: String): List<SourceEvidence>
}

interface RouteRepository {
    suspend fun save(route: Route)
    suspend fun get(id: String): Route?
    suspend fun list(): List<Route>
    suspend fun delete(id: String)
}

interface SettingsRepository {
    suspend fun get(): AppSettings
    suspend fun save(settings: AppSettings)
}
