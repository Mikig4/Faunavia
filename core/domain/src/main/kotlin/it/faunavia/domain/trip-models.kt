package it.faunavia.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class AnimalInterest { BIRDS, MAMMALS, REPTILES, AMPHIBIANS, FISH, INVERTEBRATES }

/** A place explicitly confirmed by the user; an administrative area is a bounded point sample. */
data class TripPlace(
    val name: String,
    val center: GeoPoint,
    val kind: String,
    val provenance: Provenance,
) {
    init { require(name.isNotBlank() && name.length <= 160 && kind.isNotBlank()) }
}

/** Complete chosen geometry, copied into the trip independently of network/cache availability. */
data class TripRoute(
    val geometry: Route,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val provenance: Provenance,
) {
    init {
        require(geometry.points.size >= 2)
        require(distanceMeters.isFinite() && distanceMeters > 0)
        require(durationSeconds.isFinite() && durationSeconds >= 0)
    }
}

/** An ordered, dated leg. Its departure is the trip departure or the preceding destination. */
data class TripStage(val id: String, val destination: TripPlace, val date: LocalDate, val route: TripRoute) {
    init { require(id.isNotBlank()) }
}

data class Trip(
    val id: String,
    val name: String,
    val destination: TripPlace,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val radiusMeters: Double,
    val interests: Set<AnimalInterest>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val departure: TripPlace? = null,
    val route: TripRoute? = null,
    val stages: List<TripStage> = emptyList(),
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 160)
        require(!endsOn.isBefore(startsOn) && ChronoUnit.DAYS.between(startsOn, endsOn) <= 366)
        require(radiusMeters.isFinite() && radiusMeters in 50.0..20_000.0)
        require(!updatedAt.isBefore(createdAt))
        require(route == null || departure != null)
        if (stages.isNotEmpty()) {
            require(departure != null && route != null)
            require(stages.map { it.id }.distinct().size == stages.size)
            require(stages.all { it.date in startsOn..endsOn })
            require(stages.zipWithNext().all { (a, b) -> !b.date.isBefore(a.date) })
            require(destination == stages.last().destination)
            require(route.geometry.segments == stages.flatMap { it.route.geometry.segments })
        }
    }

    /** Includes every input that can change the scope or interpretation of saved results. */
    val analysisKey: String get() {
        val legacy = listOf(destination.center.latitude, destination.center.longitude,
            startsOn, endsOn, radiusMeters, interests.sortedBy { it.name }.joinToString { it.name }).joinToString("|")
        val base = if (departure == null && route == null) legacy else "$legacy|${departure?.center}|${route?.geometry?.segments}"
        return if (stages.isEmpty()) base else "$base|${stages.map { listOf(it.id, it.date, it.destination.center, it.route.geometry.segments) }}"
    }
}

/** Copies all geometry and keeps individual provenance on each stage. No new routing request. */
fun Trip.withStages(updated: List<TripStage>, at: Instant): Trip {
    require(updated.isNotEmpty())
    val segments = updated.flatMap { it.route.geometry.segments }
    val sources = updated.map { it.route.provenance }
    val geometry = Route("$id:stages", name, segments.flatten(), at, RouteSource.PLANNED, segments = segments)
    val provenance = Provenance("Faunavia itinerary", "$id:stages", updated.joinToString { it.id },
        sources.maxOf { it.retrievedAt }, sources.map { it.license }.distinct().joinToString("; "),
        sources.map { it.attribution }.distinct().joinToString("; "),
        "Combined planned legs; individual source and quality retained per stage",
        sources.map { it.version }.distinct().joinToString("; "))
    return copy(destination = updated.last().destination, updatedAt = at, stages = updated,
        route = TripRoute(geometry, updated.sumOf { it.route.distanceMeters }, updated.sumOf { it.route.durationSeconds }, provenance))
}

/** A single stage's geometry/date for analysis; the persistent trip identity stays unchanged. */
fun Trip.stageScope(stageId: String?): Trip {
    if (stageId == null) return this
    val index = stages.indexOfFirst { it.id == stageId }
    require(index >= 0) { "Stage does not exist." }
    val stage = stages[index]
    return copy(destination = stage.destination, departure = if (index == 0) departure else stages[index - 1].destination,
        startsOn = stage.date, endsOn = stage.date, route = stage.route, stages = emptyList())
}

/** Route geometry is copied into the outing, so removing an imported route never loses the outing. */
data class Outing(
    val id: String,
    val tripId: String,
    val name: String,
    val date: LocalDate,
    val place: TripPlace,
    val route: Route? = null,
) {
    init { require(id.isNotBlank() && tripId.isNotBlank() && name.isNotBlank() && name.length <= 160) }
}

data class SavedTripPlace(val id: String, val tripId: String, val place: TripPlace, val savedAt: Instant) {
    init { require(id.isNotBlank() && tripId.isNotBlank()) }
}

/** A chosen result is an immutable evidence snapshot, never a personal sighting or an accepted taxon. */
data class SavedTripResult(
    val id: String,
    val tripId: String,
    val outingId: String?,
    val taxonId: String,
    val scientificName: String,
    val level: EvidenceLevel,
    val explanation: List<String>,
    val season: String,
    val evidence: List<SourceEvidence>,
    val analysisKey: String,
    val savedAt: Instant,
    val partial: Boolean = false,
    val stale: Boolean = false,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val area: TripPlace,
    val stageId: String? = null,
) {
    init {
        require(listOf(id, tripId, taxonId, scientificName, analysisKey).all(String::isNotBlank))
        require(explanation.isNotEmpty() && explanation.all(String::isNotBlank))
        require(evidence.all { it.taxonId == taxonId })
        require(!endsOn.isBefore(startsOn))
        require(stageId == null || (stageId.isNotBlank() && outingId == null))
    }
}

data class UnidentifiedInput(
    val id: String,
    val observedAt: Instant,
    val zoneId: ZoneId,
    val location: GeoPoint? = null,
    val notes: String = "",
    val quantity: Int = 1,
    val tripId: String? = null,
    val outingId: String? = null,
) {
    init {
        require(id.isNotBlank() && notes.length <= MAX_OBSERVATION_NOTES_LENGTH)
        require(quantity in 1..MAX_OBSERVATION_QUANTITY)
        validateMemoryLink(tripId, outingId)
    }
}

data class UnidentifiedDraft(val input: UnidentifiedInput, val createdAt: Instant, val updatedAt: Instant) {
    init { require(!updatedAt.isBefore(createdAt)) }
}

internal fun validateMemoryLink(tripId: String?, outingId: String?) {
    require(tripId == null || tripId.isNotBlank())
    require(outingId == null || (outingId.isNotBlank() && tripId != null))
}

interface TripRepository {
    suspend fun save(trip: Trip)
    suspend fun get(id: String): Trip?
    suspend fun list(): List<Trip>
    /** Unlinks memories; deletes only the trip's planning data. */
    suspend fun delete(id: String)
    suspend fun saveOuting(outing: Outing)
    suspend fun outing(id: String): Outing?
    suspend fun outings(tripId: String): List<Outing>
    suspend fun deleteOuting(id: String)
    suspend fun savePlace(place: SavedTripPlace)
    suspend fun places(tripId: String): List<SavedTripPlace>
    suspend fun deletePlace(id: String)
    suspend fun saveResult(result: SavedTripResult)
    suspend fun results(tripId: String): List<SavedTripResult>
    suspend fun deleteResult(id: String)
}

interface UnidentifiedRepository {
    suspend fun save(input: UnidentifiedInput): UnidentifiedDraft
    suspend fun get(id: String): UnidentifiedDraft?
    suspend fun list(): List<UnidentifiedDraft>
    suspend fun delete(id: String)
    suspend fun photos(draftId: String): List<ObservationPhoto>
    suspend fun addPhoto(photo: ObservationPhoto)
    suspend fun deletePhoto(id: String): ObservationPhoto?
    /** Removes metadata and the draft atomically; the Android owner then removes private files. */
    suspend fun deleteWithPhotos(id: String): List<ObservationPhoto>
    /** Inserts the identified memory and removes the draft in one transaction, retaining its identity. */
    suspend fun convert(draft: ObservationDraft): Observation
}

data class PersonalSpeciesSummary(val taxonId: String, val observations: Int, val first: Observation)

/** Pure projections use personal identified observations only. Recompute after every diary change. */
object PersonalDiaryViews {
    fun filter(entries: List<Observation>, tripId: String? = null, outingId: String? = null,
        taxonId: String? = null, date: LocalDate? = null): List<Observation> = entries.filter {
        (tripId == null || it.tripId == tripId) && (outingId == null || it.outingId == outingId) &&
            (taxonId == null || it.taxonId == taxonId) && (date == null || it.localDate == date)
    }.sortedWith(compareBy(Observation::observedAt, Observation::id))

    fun calendar(entries: List<Observation>): Map<LocalDate, List<Observation>> =
        entries.groupBy { it.localDate }.toSortedMap()

    fun map(entries: List<Observation>): List<Observation> = entries.filter { it.location != null }

    fun species(entries: List<Observation>): List<PersonalSpeciesSummary> = entries.groupBy { it.taxonId }
        .map { (id, memories) -> PersonalSpeciesSummary(id, memories.size,
            memories.minWith(compareBy(Observation::observedAt, Observation::id))) }.sortedBy { it.taxonId }
}
