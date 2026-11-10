package it.faunavia.exploration

import it.faunavia.domain.*
import it.faunavia.network.JsonHttpResponse
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OverpassOutingSearchTest {
    private val at = Instant.parse("2026-10-03T12:00:00Z")
    private val source = Provenance("fixture", "x", "synthetic", at, "CC0", "tests", "synthetic", "v1")
    private fun trip(points: List<GeoPoint>? = null, segments: List<List<GeoPoint>>? = null): Trip {
        val place = TripPlace("Arrivo pubblico", points?.last() ?: GeoPoint(45.65, 10.03), "CITY", source)
        val geometry = points?.let { Route("private-route", "Nome privato", it, at, segments = segments ?: listOf(it)) }
        return Trip("t", "Viaggio privato", place, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5),
            1_000.0, setOf(AnimalInterest.BIRDS), at, at, geometry?.let { place.copy(name = "Partenza pubblica", center = it.points.first()) },
            geometry?.let { TripRoute(it, 50_000.0, 3_000.0, source) })
    }
    private fun body(elements: String, remark: String = "") = """{"osm3s":{"timestamp_osm_base":"2026-10-03T10:00:00Z"},"elements":[$elements]$remark}"""
    private val reserve = """{"type":"way","id":123,"version":2,"center":{"lat":45.65,"lon":10.03},"tags":{"name":"Riserva","leisure":"nature_reserve","website":"https://park.example/visita","opening_hours":"09:00-18:00"}}"""
    private val hiking = """{"type":"relation","id":456,"version":3,"center":{"lat":45.651,"lon":10.031},"tags":{"name":"Sentiero","route":"hiking","distance":"2.5","duration":"01:30","sac_scale":"hiking","wheelchair":"no"}}"""

    @Test fun preservesAttributionIdentityAndOnlyDocumentedMetrics() {
        val result = parseOutingSearch(body("$reserve,$hiking"), "bounded query", at)
        assertNull(result.warning)
        val park = result.proposals.first()
        assertEquals("osm:way:123", park.id)
        assertEquals("https://www.openstreetmap.org/way/123", park.guide.source.source)
        assertEquals("ODbL 1.0", park.guide.source.license)
        assertEquals(at, park.guide.source.retrievedAt)
        assertTrue(park.guide.source.version.contains("2026-10-03T10:00:00Z"))
        assertEquals("https://park.example/visita", park.website)
        assertTrue(park.guide.accessNotes.contains("09:00-18:00"))
        assertNull(park.guide.lengthMeters); assertTrue(park.guide.species.isEmpty()); assertNull(park.route)
        val trail = result.proposals.last()
        assertEquals(2_500, trail.guide.lengthMeters); assertEquals(90, trail.guide.durationMinutes)
        assertEquals("OSM sac_scale: hiking", trail.guide.difficulty)
        assertEquals("OSM wheelchair: no", trail.guide.accessibility)
    }
    @Test fun excludesRestrictedPlacesMalformedCoordinatesAndUnrelatedRecords() {
        val restricted = reserve.replace("\"opening_hours\"", "\"access\":\"private\",\"opening_hours\"")
        val malformed = reserve.replace("45.65", "999")
        val unrelated = reserve.replace("nature_reserve", "playground")
        val result = parseOutingSearch(body("$restricted,$malformed,$unrelated,$reserve,$reserve"), "q", at)
        assertEquals(1, result.proposals.size)
        assertNotNull(result.warning)
    }
    @Test fun unknownFaunaDoesNotHidePlacesForTripsWithAnimalInterests() {
        val batch = parseOutingSearch(body(reserve), "q", at)
        val discovery = OutingDiscovery(OutingCatalogue { batch.proposals })
        assertEquals(1, discovery.discover(trip()).outings.size)
        assertTrue(discovery.discover(trip(), filters = OutingFilters(wishedOnly = true)).outings.isEmpty())
    }
    @Test fun usesClosestRoutePointRatherThanDestinationAndDoesNotBridgeSegments() {
        val scope = trip(listOf(GeoPoint(45.65, 9.0), GeoPoint(45.65, 11.0)))
        assertEquals(0.0, outingDistanceMeters(scope, GeoPoint(45.65, 10.03)), .01)
        val batch = parseOutingSearch(body(reserve), "q", at)
        assertEquals(1, OutingDiscovery(OutingCatalogue { batch.proposals }).discover(scope).outings.size)
        val planned = requireNotNull(scope.route)
        val split = scope.copy(route = planned.copy(geometry = planned.geometry.copy(
            segments = planned.geometry.points.map { listOf(it) })))
        assertTrue(outingDistanceMeters(split, GeoPoint(45.65, 10.03)) > 60_000)
    }
    @Test fun queryContainsOnlyBoundedAreasAndHonestPartialCoverage() {
        val scope = trip(listOf(GeoPoint(45.0, 5.0), GeoPoint(45.0, 20.0)))
        val plan = outingQuery(scope)
        assertTrue(plan.query.contains("out meta center 601"))
        assertEquals(12, Regex("rel\\[route=hiking\\]").findAll(plan.query).count())
        assertFalse(plan.query.contains("private-route")); assertFalse(plan.query.contains("Nome privato"))
        assertFalse(plan.query.contains("poly:")); assertNotNull(plan.warning)
        assertNull(outingQuery(trip()).warning)
    }
    @Test fun missingAndMalformedMetricsOrUnsafeLinksRemainUnknown() {
        assertEquals(90, outingDurationMinutes("01:30"))
        assertEquals(60, outingDurationMinutes("60"))
        listOf("-1", "1:99", "soon", "1000000000:00", "1:20:30").forEach { assertNull(outingDurationMinutes(it)) }
        listOf("javascript:alert(1)", "https://user:secret@example.com", "file:///tmp/x", "https:///missing").forEach { assertNull(safeOutingWebsite(it)) }
        val malformed = hiking.replace("2.5", "2.5 mi").replace("01:30", "unknown")
        val guide = parseOutingSearch(body(malformed), "q", at).proposals.single().guide
        assertNull(guide.durationMinutes); assertNull(guide.lengthMeters)
    }
    @Test fun timeoutRemarkKeepsValidPartialResultsVisible() {
        val result = parseOutingSearch(body(reserve, ",\"remark\":\"runtime timeout\""), "q", at)
        assertEquals(1, result.proposals.size); assertNotNull(result.warning)
    }
    @Test fun explicitCallsHandleRateLimitFailureAndEmptyResponseWithoutRetries() = runBlocking {
        var calls = 0
        val service = OverpassOutingSearch(OutingHttpClient { calls++; JsonHttpResponse(429, "", "60") }, { at })
        assertEquals(0, calls)
        val result = service.search(trip())
        assertEquals(1, calls); assertTrue(result.proposals.isEmpty()); assertTrue(result.warning!!.contains("Attendi"))
        assertNotNull(service.search(trip()).warning)
        assertEquals(1, calls)
        val broken = OverpassOutingSearch(OutingHttpClient { JsonHttpResponse(200, "invalid", null) }, { at }).search(trip())
        assertNotNull(broken.warning)
        val empty = OverpassOutingSearch(OutingHttpClient { JsonHttpResponse(200, body(""), null) }, { at }).search(trip())
        assertTrue(empty.proposals.isEmpty()); assertNull(empty.warning)
    }
    @Test fun googleLinkUsesPublicLabelsOnly() {
        val url = outingWebSearchUrl(trip(listOf(GeoPoint(45.65, 9.0), GeoPoint(45.65, 11.0))))
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("Partenza+pubblica")); assertTrue(url.contains("Arrivo+pubblico"))
        assertFalse(url.contains("45.65")); assertFalse(url.contains("Nome+privato"))
    }
}
