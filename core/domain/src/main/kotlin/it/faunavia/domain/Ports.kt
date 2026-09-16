package it.faunavia.domain

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be valid WGS84." }
        require(longitude in -180.0..180.0) { "Longitude must be valid WGS84." }
    }
}

data class SpeciesSummary(
    val taxonId: String,
    val scientificName: String,
    val evidenceLevel: EvidenceLevel,
)

enum class EvidenceLevel {
    DOCUMENTED,
    PLAUSIBLE,
    INSUFFICIENT,
}

interface AppClock {
    fun nowEpochMillis(): Long
}

interface LocationSource {
    fun currentLocation(): GeoPoint?
}

interface SpeciesProvider {
    fun speciesNear(point: GeoPoint): List<SpeciesSummary>
}
