package it.faunavia.route

import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteSource
import java.time.Instant
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEngineTest {
    private val engine = RouteEngine()
    private val importedAt = Instant.parse("2026-09-18T12:00:00Z")

    @Test
    fun producesDeterministicMetricCellsChunksCorridorAndFingerprint() {
        val route = route(
            listOf(
                listOf(GeoPoint(45.5200, 9.1800), GeoPoint(45.5250, 9.1850)),
                listOf(GeoPoint(45.5300, 9.1900), GeoPoint(45.5350, 9.1950)),
            ),
        )

        val first = engine.analyze(route)
        val second = engine.analyze(route.copy(name = "Un altro nome", importedAt = importedAt.plusSeconds(60)))

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.cells, second.cells)
        assertEquals(first.queryChunks, second.queryChunks)
        assertEquals("EPSG:3035", first.projection)
        assertEquals(2, first.corridorPortions.map { it.segmentIndex }.distinct().size)
        assertEquals(first.cells.map { it.key }.toSet().size, first.cells.size)
        assertTrue(first.queryChunks.all { it.cellKeys.isNotEmpty() })
    }

    @Test
    fun samplesByDistanceWithoutBridgingSegments() {
        val route = route(
            listOf(
                listOf(GeoPoint(45.5200, 9.1800), GeoPoint(45.5400, 9.1800)),
                listOf(GeoPoint(45.6000, 9.2500)),
            ),
        )
        val analysis = engine.analyze(route, RouteAnalysisConfig(samplingIntervalMeters = 500.0))

        assertTrue(analysis.samples.size >= 6)
        assertEquals(1, analysis.corridorPortions.count { it.segmentIndex == 1 })
        assertTrue(analysis.corridorPortions.none { portion ->
            portion.segmentIndex == 0 && portion.polygon.any { it.latitude >= 45.58 }
        })
    }

    @Test
    fun pointCreatesCircularCorridorAndMetricCells() {
        val route = route(listOf(listOf(GeoPoint(45.5200, 9.1800))), RouteSource.LOCATION)
        val analysis = engine.analyze(route)

        assertEquals(0.0, analysis.totalLengthMeters, 0.0)
        assertEquals(1, analysis.samples.size)
        assertEquals(1, analysis.corridorPortions.size)
        assertTrue(analysis.cells.isNotEmpty())
        assertEquals(Math.PI * 1_000.0.pow(2), analysis.estimatedCorridorAreaSquareMeters, 0.001)
    }

    @Test
    fun configurationChangesFingerprintAndCoverage() {
        val route = route(listOf(listOf(GeoPoint(45.52, 9.18))))
        val narrow = engine.analyze(route, RouteAnalysisConfig(corridorRadiusMeters = 500.0))
        val wide = engine.analyze(route, RouteAnalysisConfig(corridorRadiusMeters = 2_000.0))

        assertNotEquals(narrow.fingerprint, wide.fingerprint)
        assertTrue(wide.cells.size > narrow.cells.size)
    }

    @Test
    fun epsg3035RoundTripAndMetricScaleStayAccurateInPilotArea() {
        val original = GeoPoint(45.4642, 9.1900)
        val projected = Epsg3035.forward(original)
        val roundTrip = Epsg3035.inverse(projected)
        val oneKilometerEast = Epsg3035.inverse(ProjectedPoint(projected.easting + 1_000.0, projected.northing))

        assertEquals(original.latitude, roundTrip.latitude, 1e-8)
        assertEquals(original.longitude, roundTrip.longitude, 1e-8)
        assertEquals(1_000.0, haversine(original, oneKilometerEast), 5.0)
    }

    @Test
    fun antimeridianAndOutsideEuropeFailExplicitly() {
        val crossing = route(listOf(listOf(GeoPoint(45.0, 179.5), GeoPoint(45.0, -179.5))))
        val outside = route(listOf(listOf(GeoPoint(-33.9, 18.4))))

        assertCode(RouteImportErrorCode.ANTIMERIDIAN_UNSUPPORTED) { engine.analyze(crossing) }
        assertCode(RouteImportErrorCode.OUTSIDE_EPSG_3035_AREA) { engine.analyze(outside) }
    }

    private fun route(segments: List<List<GeoPoint>>, source: RouteSource = RouteSource.GEOJSON): Route = Route(
        id = "route",
        name = "Fixture",
        points = segments.flatten(),
        importedAt = importedAt,
        source = source,
        segments = segments,
    )

    private fun assertCode(expected: RouteImportErrorCode, block: () -> Unit) {
        val error = assertThrows(RouteImportException::class.java, block)
        assertEquals(expected, error.code)
    }

    private fun haversine(start: GeoPoint, end: GeoPoint): Double {
        val deltaLatitude = Math.toRadians(end.latitude - start.latitude)
        val deltaLongitude = Math.toRadians(end.longitude - start.longitude)
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val a = sin(deltaLatitude / 2.0).pow(2) +
            cos(startLatitude) * cos(endLatitude) * sin(deltaLongitude / 2.0).pow(2)
        return 6_371_008.8 * 2.0 * asin(sqrt(a))
    }
}
