package it.faunavia.domain

import java.time.Instant

/** General site documentation, never a precise animal location or a current sighting. */
data class OutingSpecies(val scientificName: String, val commonName: String, val interest: AnimalInterest,
    val recommendedMonths: Set<Int>, val source: Provenance) {
    init {
        require(scientificName.isNotBlank() && scientificName.length <= 160)
        require(commonName.isNotBlank() && commonName.length <= 160)
        require(recommendedMonths.all { it in 1..12 })
    }
}

/** Durable factual summary copied into the existing outing payload and included in local backup. */
data class OutingGuide(val catalogueId: String, val catalogueVersion: String, val savedAt: Instant,
    val startLabel: String, val lengthMeters: Int?, val durationMinutes: Int?, val difficulty: String?,
    val accessNotes: String, val accessibility: String?, val species: List<OutingSpecies>,
    val source: Provenance, val accessSource: Provenance, val coordinateSource: Provenance) {
    init {
        require(catalogueId.isNotBlank() && catalogueId.length <= 160 && catalogueVersion.isNotBlank())
        require(startLabel.isNotBlank() && startLabel.length <= 512)
        require(lengthMeters == null || lengthMeters in 1..200_000)
        require(durationMinutes == null || durationMinutes in 1..10_080)
        require(difficulty == null || (difficulty.isNotBlank() && difficulty.length <= 160))
        require(accessNotes.isNotBlank() && accessNotes.length <= 4_096)
        require(accessibility == null || (accessibility.isNotBlank() && accessibility.length <= 1_024))
        require(species.size <= 50 && species.map { it.scientificName }.distinct().size == species.size)
    }
}
