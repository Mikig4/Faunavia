package it.faunavia.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TripModelsTest {
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val place = TripPlace("Milano", GeoPoint(45.46, 9.19), "CITY",
        Provenance("fixture", "1", "Milano", now, "CC0", "Test", "synthetic", "v1"))
    private val trip = Trip("t", "Vacanza", place, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7),
        1_000.0, setOf(AnimalInterest.BIRDS), now, now)

    @Test fun invalidDatesRadiusAndMemoryLinksAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { trip.copy(endsOn = trip.startsOn.minusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { trip.copy(endsOn = trip.startsOn.plusDays(367)) }
        listOf(Double.NaN, 49.0, 20_001.0).forEach {
            assertThrows(IllegalArgumentException::class.java) { trip.copy(radiusMeters = it) }
        }
        assertThrows(IllegalArgumentException::class.java) { UnidentifiedInput("d", now, ZoneId.of("UTC"), outingId = "o") }
        assertThrows(IllegalArgumentException::class.java) { ObservationDraft("o", "taxon", now, ZoneId.of("UTC"), tripId = "") }
    }

    private fun stage(id: String, destination: TripPlace, day: LocalDate, from: TripPlace = place) = TripStage(id, destination, day,
        TripRoute(Route(id, id, listOf(from.center, destination.center), now, RouteSource.PLANNED),
            1_000.0, 60.0, destination.provenance))

    @Test fun datedStagesPreserveGeometryAndScopeAndAllowSameDayOrRestDays() {
        val middle = place.copy(name = "Verona", center = GeoPoint(45.44, 10.99))
        val end = place.copy(name = "Venezia", center = GeoPoint(45.44, 12.33))
        val a = stage("a", middle, trip.startsOn)
        val b = stage("b", end, trip.startsOn.plusDays(2), middle)
        val planned = trip.copy(departure = place).withStages(listOf(a, b), now)
        assertEquals(end, planned.destination)
        assertEquals(listOf(a.route.geometry.points, b.route.geometry.points), planned.route!!.geometry.segments)
        assertEquals(2_000.0, planned.route.distanceMeters, 0.0)
        val scoped = planned.stageScope("b")
        assertEquals(middle, scoped.departure)
        assertEquals(b.date, scoped.startsOn)
        assertEquals(b.date, scoped.endsOn)
        assertEquals(b.route, scoped.route)
        assertNotEquals(planned.analysisKey, planned.withStages(listOf(a, b.copy(date = a.date)), now).analysisKey)
        assertEquals(planned, planned.stageScope(null))
    }

    @Test fun invalidStageDatesDuplicateIdsAndIncompleteCombinedTraceAreRejected() {
        val end = place.copy(center = GeoPoint(46.0, 10.0))
        val a = stage("a", end, trip.startsOn.plusDays(1))
        val base = trip.copy(departure = place)
        assertThrows(IllegalArgumentException::class.java) { base.withStages(emptyList(), now) }
        assertThrows(IllegalArgumentException::class.java) { base.withStages(listOf(a, a), now) }
        assertThrows(IllegalArgumentException::class.java) { base.withStages(listOf(a, a.copy(id = "b", date = trip.startsOn)), now) }
        assertThrows(IllegalArgumentException::class.java) { base.withStages(listOf(a.copy(date = trip.endsOn.plusDays(1))), now) }
        val planned = base.withStages(listOf(a), now)
        assertThrows(IllegalArgumentException::class.java) { planned.copy(destination = place) }
        assertThrows(IllegalArgumentException::class.java) { planned.copy(startsOn = a.date.plusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { planned.stageScope("removed") }
    }

    @Test fun selectedGeometryChangesAnalysisIdentityAndRequiresDeparture() {
        val geometry = Route("planned", "Milano → Como", listOf(place.center, GeoPoint(45.8, 9.1)), now, RouteSource.PLANNED)
        val route = TripRoute(geometry, 50_000.0, 3_600.0, place.provenance)
        assertThrows(IllegalArgumentException::class.java) { trip.copy(route = route) }
        val planned = trip.copy(departure = place, route = route)
        assertNotEquals(trip.analysisKey, planned.analysisKey)
        val changed = geometry.copy(points = listOf(place.center, GeoPoint(45.7, 9.2)),
            segments = listOf(listOf(place.center, GeoPoint(45.7, 9.2))))
        assertNotEquals(planned.analysisKey, planned.copy(route = route.copy(geometry = changed)).analysisKey)
        assertEquals(planned.analysisKey, planned.copy(name = "Vacanza rinominata").analysisKey)
    }

    @Test fun analysisIdentityChangesWithAreaDatesAndInterestsButNotName() {
        assertEquals(trip.analysisKey, trip.copy(name = "Nuovo nome").analysisKey)
        assertNotEquals(trip.analysisKey, trip.copy(startsOn = trip.startsOn.plusDays(1)).analysisKey)
        assertNotEquals(trip.analysisKey, trip.copy(radiusMeters = 2_000.0).analysisKey)
        assertNotEquals(trip.analysisKey, trip.copy(destination = place.copy(center = GeoPoint(46.0, 9.0))).analysisKey)
        assertNotEquals(trip.analysisKey, trip.copy(interests = setOf(AnimalInterest.MAMMALS)).analysisKey)
        assertEquals(trip.copy(interests = setOf(AnimalInterest.BIRDS, AnimalInterest.MAMMALS)).analysisKey,
            trip.copy(interests = setOf(AnimalInterest.MAMMALS, AnimalInterest.BIRDS)).analysisKey)
    }

    @Test fun calendarMapAndSpeciesFiltersKeepUnlocatedMemoriesAndUseTheirLocalDay() {
        val a = observation("a", "bird", now, null, "t")
        val b = observation("b", "bird", now.minusSeconds(86_400), GeoPoint(45.0, 9.0), "t")
        val c = observation("c", "mammal", now, GeoPoint(45.1, 9.1), "other")
        val entries = listOf(a, b, c)
        assertEquals(listOf(b, a), PersonalDiaryViews.filter(entries, tripId = "t", taxonId = "bird"))
        assertEquals(listOf(a), PersonalDiaryViews.filter(entries, tripId = "t", date = a.localDate))
        assertEquals(3, PersonalDiaryViews.calendar(entries).values.sumOf { it.size })
        assertEquals(listOf(b, c), PersonalDiaryViews.map(entries))
        val summary = PersonalDiaryViews.species(entries).first { it.taxonId == "bird" }
        assertEquals(2, summary.observations)
        assertEquals(b, summary.first)
    }

    @Test fun firstObservationIsRecomputedAfterEditAndDeleteWithStableTies() {
        val a = observation("a", "bird", now, null, null)
        val b = observation("b", "bird", now, null, null)
        assertEquals("a", PersonalDiaryViews.species(listOf(b, a)).single().first.id)
        assertEquals("b", PersonalDiaryViews.species(listOf(b, a.copy(observedAt = now.plusSeconds(1)))).single().first.id)
        assertEquals(1, PersonalDiaryViews.species(listOf(b)).single().observations)
        assertTrue(PersonalDiaryViews.species(emptyList()).isEmpty())
    }

    @Test fun draftDateAndNotesDoNotRequireOrInventATaxon() {
        val input = UnidentifiedInput("d", now, ZoneId.of("Europe/Rome"), notes = "Uccello scuro", tripId = "t")
        assertEquals(input, UnidentifiedDraft(input, now, now).input)
        assertThrows(IllegalArgumentException::class.java) { input.copy(notes = "x".repeat(2_001)) }
    }

    private fun observation(id: String, taxon: String, time: Instant, point: GeoPoint?, trip: String?) =
        Observation(id, taxon, time, ZoneId.of("Europe/Rome"), point, "", now, now, tripId = trip)
}
