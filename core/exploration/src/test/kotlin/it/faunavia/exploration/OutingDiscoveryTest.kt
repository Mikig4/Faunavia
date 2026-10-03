package it.faunavia.exploration

import it.faunavia.domain.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class OutingDiscoveryTest {
    private val now = Instant.parse("2026-10-03T14:35:42Z")
    private val entries = PilotOutingCatalogue.load()
    private fun trip(entry: OutingProposal = entries.first(), date: String = "2026-10-03", interests: Set<AnimalInterest> = emptySet()) =
        Trip("trip", "Vacanza", entry.place, LocalDate.parse(date), LocalDate.parse(date).plusDays(7), 20_000.0, interests, now, now)
    private fun wished(name: String) = Taxon("test:$name", name, null, "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES", entries.first().guide.source)

    @Test fun pilotHasBoundedReusableSummariesPublicCoordinatesAndCompleteSources() {
        assertEquals(5, entries.size)
        assertTrue(entries.all { it.publicLocation && it.route == null })
        assertTrue(entries.all { it.guide.difficulty == null && it.guide.accessibility == null })
        assertEquals(3, OutingDiscovery().discover(trip()).outings.size)
        assertTrue(entries.all { it.guide.species.all { animal -> animal.source.source.startsWith("https:") } })
        assertTrue(entries.first { it.id == "sebino-nord-centrale" }.guide.accessNotes.contains("parzialmente"))
    }
    @Test fun destinationsWithoutCoverageAndProviderFailureRemainDistinct() {
        assertTrue(OutingDiscovery().discover(trip().copy(destination = entries.first().place.copy(center = GeoPoint(51.5, -.1)))).outings.isEmpty())
        val failure = OutingDiscovery(OutingCatalogue { throw IllegalStateException("unavailable") }).discover(trip())
        assertTrue(failure.outings.isEmpty()); assertNotNull(failure.warning)
    }
    @Test fun datesInterestAndWishlistUseOnlyDocumentedSpeciesAndPeriods() {
        val sebino = trip(interests = setOf(AnimalInterest.BIRDS))
        assertEquals(3, OutingDiscovery().discover(sebino, listOf(wished("Alcedo atthis")), OutingFilters(wishedOnly = true, seasonalOnly = true)).outings.size)
        assertTrue(OutingDiscovery().discover(trip(date = "2026-02-03"), filters = OutingFilters(seasonalOnly = true)).outings.isEmpty())
        assertTrue(OutingDiscovery().discover(trip(interests = setOf(AnimalInterest.FISH))).outings.isEmpty())
        val brabbia = trip(entries.first { it.id == "brabbia-sentieri" }, "2026-01-03")
        assertEquals("Turdus iliacus", OutingDiscovery().discover(brabbia).outings.single().seasonalSpecies.single().scientificName)
        assertTrue(OutingDiscovery().discover(brabbia, listOf(wished("Turdus merula").copy(kingdom = "Plantae")), OutingFilters(wishedOnly = true)).outings.isEmpty())
    }
    @Test fun missingMetricsAreNotInventedAndDocumentedFiltersExcludeThem() {
        val brabbia = trip(entries.first { it.id == "brabbia-sentieri" })
        val found = OutingDiscovery().discover(brabbia).outings.single()
        assertNull(found.proposal.guide.lengthMeters); assertNull(found.proposal.guide.durationMinutes)
        assertTrue(OutingDiscovery().discover(brabbia, filters = OutingFilters(maxDurationMinutes = 120)).outings.isEmpty())
        assertEquals(2, OutingDiscovery().discover(trip(), filters = OutingFilters(maxDurationMinutes = 120)).outings.size)
        assertEquals(1, OutingDiscovery().discover(trip(), filters = OutingFilters(maxLengthMeters = 2_000)).outings.size)
        assertTrue(OutingDiscovery().discover(trip(), filters = OutingFilters(difficulty = "facile")).outings.isEmpty())
    }
    @Test fun orderingIsStableAndDurationDistanceHaveDistinctMeanings() {
        val service = OutingDiscovery(OutingCatalogue { entries.reversed() + entries.first() })
        val duration = service.discover(trip(), filters = OutingFilters(sort = OutingSort.DURATION)).outings
        assertEquals(listOf("sebino-tutti", "sebino-nord-centrale", "sebino-nord-sud"), duration.map { it.proposal.id })
        assertEquals(duration, service.discover(trip(), filters = OutingFilters(sort = OutingSort.DURATION)).outings)
        assertEquals(0.0, duration.first().distanceMeters, .001)
        assertTrue(geographicDistanceMeters(GeoPoint(45.0, 9.0), GeoPoint(45.1, 9.0)) in 11_000.0..11_200.0)
    }
    @Test fun savingIsDeterministicDateCheckedAndCopiesDocumentationWithoutNetwork() {
        val proposal = entries.first()
        val trip = trip()
        val first = proposal.saveFor(trip, trip.startsOn, now)
        assertEquals(first, proposal.saveFor(trip, trip.startsOn, now))
        assertNotEquals(first.id, proposal.saveFor(trip, trip.startsOn.plusDays(1), now).id)
        assertEquals(proposal.guide.source, first.guide!!.source)
        assertNull(first.route)
        assertThrows(IllegalArgumentException::class.java) { proposal.saveFor(trip, trip.endsOn.plusDays(1), now) }
    }
    @Test fun sensitiveLocationsAreExcludedAndCannotBeSavedEvenWithGeometry() {
        val entry = entries.first().copy(publicLocation = false)
        assertTrue(OutingDiscovery(OutingCatalogue { listOf(entry) }).discover(trip()).outings.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { entry.saveFor(trip(), trip().startsOn, now) }
    }
    @Test fun originalSegmentsAreCopiedWithoutCreatingAConnectingTrail() {
        val points = listOf(GeoPoint(45.65, 10.03), GeoPoint(45.66, 10.04), GeoPoint(45.67, 10.04), GeoPoint(45.68, 10.03))
        val route = Route("public", "Traccia documentata", points, now, RouteSource.GEOJSON, segments = listOf(points.take(2), points.takeLast(2)))
        val outing = entries.first().copy(route = route).saveFor(trip(), trip().startsOn, now)
        assertEquals(route, outing.route)
        assertEquals(2, outing.route!!.segments.size)
    }
}
