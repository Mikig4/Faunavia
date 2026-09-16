package it.faunavia.testing

import it.faunavia.domain.EvidenceLevel
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.SpeciesSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class FakesTest {
    @Test
    fun fakeClockIsMutableAndDeterministic() {
        val clock = FakeClock(1_000)
        assertEquals(1_000, clock.nowEpochMillis())
        clock.epochMillis = 2_000
        assertEquals(2_000, clock.nowEpochMillis())
    }

    @Test
    fun fakeProviderRecordsRequests() {
        val result = SpeciesSummary("gbif:1", "Animalia example", EvidenceLevel.INSUFFICIENT)
        val provider = FakeSpeciesProvider(listOf(result))
        val point = GeoPoint(45.52, 9.19)

        assertEquals(listOf(result), provider.speciesNear(point))
        assertEquals(listOf(point), provider.requests)
    }
}
