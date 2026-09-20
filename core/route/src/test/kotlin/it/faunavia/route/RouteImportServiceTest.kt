package it.faunavia.route

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteImportServiceTest {
    private val repository = RecordingRouteRepository()
    private val clock = object : AppClock {
        override fun nowEpochMillis(): Long = 1_758_196_800_000L
    }
    private val service = RouteImportService(clock, repository)

    @Test
    fun validImportIsAnalyzedThenDeduplicatedByGeometryAcrossConfigurations() = runBlocking {
        val first = service.importAndSave("route.geojson", GEOJSON)
        val second = service.importAndSave(
            "copy.geojson",
            GEOJSON,
            RouteAnalysisConfig(corridorRadiusMeters = 2_000.0),
        )

        assertTrue(first.analysis.fingerprint != second.analysis.fingerprint)
        assertEquals(first.route.id, second.route.id)
        assertEquals(1, repository.routes.size)
        assertEquals(first.route, repository.get(first.route.id))
    }

    @Test
    fun invalidImportHasNoPersistenceSideEffects() {
        assertThrows(RouteImportException::class.java) {
            runBlocking { service.importAndSave("invalid.geojson", """{"type":"Point","coordinates":[9,99]}""") }
        }
        assertTrue(repository.routes.isEmpty())
    }

    @Test
    fun locationUsesTheSameDeterministicPipeline() = runBlocking {
        val outcome = service.saveLocation("Posizione", GeoPoint(45.52, 9.18))

        assertEquals(1, outcome.route.points.size)
        assertEquals(0.0, outcome.analysis.totalLengthMeters, 0.0)
        assertEquals(outcome.route, repository.get(outcome.route.id))
    }

    private class RecordingRouteRepository : RouteRepository {
        val routes = linkedMapOf<String, Route>()
        override suspend fun save(route: Route) { routes[route.id] = route }
        override suspend fun get(id: String): Route? = routes[id]
        override suspend fun list(): List<Route> = routes.values.toList()
        override suspend fun delete(id: String) { routes.remove(id) }
    }

    private companion object {
        val GEOJSON = """
            {"type":"Feature","properties":{"name":"Naviglio"},"geometry":{"type":"LineString","coordinates":[[9.17,45.45],[9.18,45.46],[9.19,45.47]]}}
        """.trimIndent()
    }
}
