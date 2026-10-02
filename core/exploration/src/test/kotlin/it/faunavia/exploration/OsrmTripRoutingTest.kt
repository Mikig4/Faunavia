package it.faunavia.exploration

import it.faunavia.domain.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OsrmTripRoutingTest {
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val provenance = Provenance("fixture", "p", "route", now, "CC0", "Test", "synthetic", "v1")
    private val start = TripPlace("Milano", GeoPoint(45.0, 9.0), "CITY", provenance)
    private val end = TripPlace("Como", GeoPoint(45.1, 9.1), "CITY", provenance)
    private val first = """{"distance":15000,"duration":1200,"geometry":{"type":"LineString","coordinates":[[9,45],[9.02,45.04],[9.1,45.1]]}}"""
    private val second = """{"distance":18000,"duration":1500,"geometry":{"type":"LineString","coordinates":[[9,45],[9.08,45.02],[9.1,45.1]]}}"""
    private fun response(routes: String = "$first,$second") = """{"code":"Ok","data_version":"2026-09-30","routes":[$routes]}"""

    @Test fun alternativesPreserveAllVerticesAndProvenanceAndSendOnlyEndpoints() = runBlocking {
        var requested = ""
        val planner = OsrmTripRouting(RoutingHttpClient { requested = it; response() }, { now.toEpochMilli() })
        val result = planner.plan(start, end) as TripRoutingResult.Routes
        assertEquals(2, result.choices.size)
        val choice = result.choices.first()
        assertEquals(listOf(start.center, GeoPoint(45.04, 9.02), end.center), choice.geometry.points)
        assertEquals(RouteSource.PLANNED, choice.geometry.source)
        assertEquals(15_000.0, choice.distanceMeters, 0.0)
        assertEquals("ODbL", choice.provenance.license)
        assertEquals("2026-09-30", choice.provenance.version)
        assertEquals(now, choice.provenance.retrievedAt)
        assertTrue(requested.contains("/9.0,45.0;9.1,45.1?"))
        assertTrue(requested.contains("overview=full"))
        assertFalse(requested.contains("Milano"))
        assertNotEquals(result.choices[0].geometry.id, result.choices[1].geometry.id)
    }

    @Test fun equivalentGeometryHasStableIdentityAndDuplicateAlternativesAreRemoved() = runBlocking {
        val a = OsrmTripRouting(RoutingHttpClient { response("$first,$first") }, { now.toEpochMilli() })
            .plan(start, end) as TripRoutingResult.Routes
        val b = OsrmTripRouting(RoutingHttpClient { response(first) }, { now.plusSeconds(30).toEpochMilli() })
            .plan(start, end) as TripRoutingResult.Routes
        assertEquals(1, a.choices.size)
        assertEquals(a.choices.single().geometry.id, b.choices.single().geometry.id)
    }

    @Test fun cacheAndExplicitRequestsRespectTheOnePerSecondLimit() = runBlocking {
        var time = now.toEpochMilli()
        var calls = 0
        val waits = mutableListOf<Long>()
        val planner = OsrmTripRouting(RoutingHttpClient { calls++; response(first) }, { time }, { waits += it; time += it })
        planner.plan(start, end)
        planner.plan(start, end)
        assertEquals(1, calls)
        planner.plan(start.copy(center = GeoPoint(45.01, 9.0)), end)
        assertEquals(listOf(1_000L), waits)
        time += 600_001
        planner.plan(start, end)
        assertEquals(3, calls)
    }

    @Test fun unavailableMalformedAndNoRouteNeverBecomeAStraightLine() = runBlocking {
        val offline = OsrmTripRouting(RoutingHttpClient { throw IOException("offline") })
        assertEquals(TripRoutingResult.Unavailable, offline.plan(start, end))
        listOf("bad json", """{"code":"Ok","routes":[]}""", response(first.replace("LineString", "Point"))).forEach { body ->
            assertEquals(TripRoutingResult.Unavailable, OsrmTripRouting(RoutingHttpClient { body }).plan(start, end))
        }
        assertEquals(TripRoutingResult.NoRoute,
            OsrmTripRouting(RoutingHttpClient { """{"code":"NoRoute"}""" }).plan(start, end))
        assertEquals(TripRoutingResult.NoRoute, OsrmTripRouting(RoutingHttpClient { error("No request expected") }).plan(start, start))
    }
}
