package it.faunavia.testing

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.LocationSource
import it.faunavia.domain.SpeciesProvider
import it.faunavia.domain.SpeciesSummary

class FakeClock(
    var epochMillis: Long,
) : AppClock {
    override fun nowEpochMillis(): Long = epochMillis
}

class FakeLocationSource(
    var location: GeoPoint?,
) : LocationSource {
    override fun currentLocation(): GeoPoint? = location
}

class FakeSpeciesProvider(
    private val results: List<SpeciesSummary> = emptyList(),
) : SpeciesProvider {
    val requests = mutableListOf<GeoPoint>()

    override fun speciesNear(point: GeoPoint): List<SpeciesSummary> {
        requests += point
        return results
    }
}
