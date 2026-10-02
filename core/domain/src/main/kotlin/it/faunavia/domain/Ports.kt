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

enum class EvidenceLevel {
    DOCUMENTED,
    PLAUSIBLE,
    INSUFFICIENT,
}

interface AppClock {
    fun nowEpochMillis(): Long
}
