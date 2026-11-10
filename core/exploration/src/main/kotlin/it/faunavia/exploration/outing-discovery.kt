package it.faunavia.exploration

import it.faunavia.domain.*
import java.time.Instant
import java.time.LocalDate
import kotlin.math.*

data class OutingProposal(val id: String, val name: String, val place: TripPlace, val guide: OutingGuide,
    val route: Route? = null, val publicLocation: Boolean = true, val website: String? = null) {
    init { require(id == guide.catalogueId && name.isNotBlank() && name.length <= 160) }
    fun saveFor(trip: Trip, date: LocalDate, at: Instant): Outing {
        require(publicLocation) { "Una località sensibile non può diventare una proposta pubblica." }
        require(date in trip.startsOn..trip.endsOn) { "Scegli una data compresa nel viaggio." }
        // Stable identity prevents repeated taps from duplicating the same proposal on the same day.
        return Outing("f17:${trip.id}:$id:$date", trip.id, name, date, place, route, guide.copy(savedAt = at))
    }
}

fun interface OutingCatalogue { fun load(): List<OutingProposal> }
enum class OutingSort { RELEVANCE, DISTANCE, DURATION }
data class OutingFilters(val date: LocalDate? = null, val maxDurationMinutes: Int? = null,
    val maxLengthMeters: Int? = null, val difficulty: String? = null, val wishedOnly: Boolean = false,
    val seasonalOnly: Boolean = false, val sort: OutingSort = OutingSort.RELEVANCE) {
    init {
        require(maxDurationMinutes == null || maxDurationMinutes > 0)
        require(maxLengthMeters == null || maxLengthMeters > 0)
    }
}
data class DiscoveredOuting(val proposal: OutingProposal, val distanceMeters: Double,
    val species: List<OutingSpecies>, val wishedSpecies: List<OutingSpecies>, val seasonalSpecies: List<OutingSpecies>)
data class OutingDiscoveryResult(val outings: List<DiscoveredOuting>, val warning: String? = null)

/** Ranking general place documentation never changes F7 or creates a diary sighting. */
class OutingDiscovery(private val catalogue: OutingCatalogue) {
    fun discover(trip: Trip, wishedTaxa: List<Taxon> = emptyList(), filters: OutingFilters = OutingFilters()): OutingDiscoveryResult {
        require(filters.date == null || filters.date in trip.startsOn..trip.endsOn)
        val entries = try { catalogue.load() } catch (_: Exception) {
            return OutingDiscoveryResult(emptyList(), "Catalogo uscite non disponibile. Le uscite salvate, i percorsi importati e la pianificazione manuale restano disponibili.")
        }
        val months = mutableSetOf<Int>()
        var date = filters.date ?: trip.startsOn
        val end = filters.date ?: trip.endsOn
        while (!date.isAfter(end)) { months += date.monthValue; date = date.plusDays(1) }
        val wishedNames = wishedTaxa.filter(Taxon::isSelectable).map { scientificIdentity(it.scientificName) }.toSet()
        val candidates = entries.filter { it.publicLocation }.distinctBy { it.id }.mapNotNull { entry ->
            val distance = outingDistanceMeters(trip, entry.place.center)
            if (distance > trip.radiusMeters) return@mapNotNull null
            val guide = entry.guide
            if (filters.maxDurationMinutes != null && (guide.durationMinutes?.let { it <= filters.maxDurationMinutes } != true)) return@mapNotNull null
            if (filters.maxLengthMeters != null && (guide.lengthMeters?.let { it <= filters.maxLengthMeters } != true)) return@mapNotNull null
            if (filters.difficulty != null && guide.difficulty != filters.difficulty) return@mapNotNull null
            val species = guide.species.filter { trip.interests.isEmpty() || it.interest in trip.interests }
            if (species.isEmpty() && guide.species.isNotEmpty() && trip.interests.isNotEmpty()) return@mapNotNull null
            val wishes = species.filter { scientificIdentity(it.scientificName) in wishedNames }
            val seasonal = species.filter { it.recommendedMonths.any(months::contains) }
            if (filters.wishedOnly && wishes.isEmpty()) return@mapNotNull null
            if (filters.seasonalOnly && seasonal.isEmpty()) return@mapNotNull null
            DiscoveredOuting(entry, distance, species, wishes, seasonal)
        }
        val comparator = when (filters.sort) {
            OutingSort.RELEVANCE -> compareByDescending<DiscoveredOuting> { it.wishedSpecies.size }
                .thenByDescending { it.seasonalSpecies.size }.thenByDescending { it.species.size }.thenBy { it.distanceMeters }
            OutingSort.DISTANCE -> compareBy { it.distanceMeters }
            OutingSort.DURATION -> compareBy { it.proposal.guide.durationMinutes ?: Int.MAX_VALUE }
        }
        return OutingDiscoveryResult(candidates.sortedWith(comparator.thenBy { it.proposal.id }))
    }
}

/** Nearest point on each original segment; separated legs never create a connecting edge. */
fun outingDistanceMeters(trip: Trip, point: GeoPoint): Double {
    val route = trip.route?.geometry ?: return geographicDistanceMeters(trip.destination.center, point)
    val scale = 6_371_008.8 * Math.PI / 180
    val longitudeScale = scale * cos(Math.toRadians(point.latitude))
    fun x(p: GeoPoint) = (p.longitude - point.longitude) * longitudeScale
    fun y(p: GeoPoint) = (p.latitude - point.latitude) * scale
    return route.segments.minOf { segment ->
        if (segment.size == 1) geographicDistanceMeters(segment.first(), point) else segment.zipWithNext().minOf { (a, b) ->
            val ax = x(a); val ay = y(a); val dx = x(b) - ax; val dy = y(b) - ay
            val square = dx * dx + dy * dy
            val fraction = if (square == 0.0) 0.0 else (-(ax * dx + ay * dy) / square).coerceIn(0.0, 1.0)
            hypot(ax + fraction * dx, ay + fraction * dy)
        }
    }
}

/** Great-circle distance to a public reference point; never a transfer time or walking length. */
fun geographicDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 6_371_008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
