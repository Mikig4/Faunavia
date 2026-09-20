package it.faunavia.route

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteRepository
import it.faunavia.domain.RouteSource
import java.time.Instant

class RouteImportService(
    private val clock: AppClock,
    private val repository: RouteRepository,
    private val parser: RouteDocumentParser = RouteDocumentParser(),
    private val engine: RouteEngine = RouteEngine(),
) {
    suspend fun importAndSave(
        fileName: String,
        content: String,
        config: RouteAnalysisConfig = RouteAnalysisConfig(),
    ): RouteImportOutcome {
        val parsed = parser.parse(fileName, content, Instant.ofEpochMilli(clock.nowEpochMillis()))
        return analyzeAndSave(parsed.route, config, parsed.warnings)
    }

    suspend fun saveLocation(
        name: String,
        point: GeoPoint,
        config: RouteAnalysisConfig = RouteAnalysisConfig(),
    ): RouteImportOutcome {
        require(name.isNotBlank()) { "A location analysis needs a name." }
        val route = Route(
            id = "unpersisted",
            name = name.trim().take(160),
            points = listOf(point),
            importedAt = Instant.ofEpochMilli(clock.nowEpochMillis()),
            source = RouteSource.LOCATION,
            segments = listOf(listOf(point)),
        )
        return analyzeAndSave(route, config, emptyList())
    }

    fun analyze(
        route: Route,
        config: RouteAnalysisConfig = RouteAnalysisConfig(),
    ): RouteAnalysis = engine.analyze(route, config)

    private suspend fun analyzeAndSave(
        route: Route,
        config: RouteAnalysisConfig,
        warnings: List<RouteImportWarning>,
    ): RouteImportOutcome {
        val provisional = engine.analyze(route, config, warnings)
        val candidate = route.copy(id = "route-${engine.geometryFingerprint(route).take(24)}")
        val stored = repository.get(candidate.id) ?: candidate.also { repository.save(it) }
        val analysis = engine.analyze(stored, config, warnings)
        return RouteImportOutcome(stored, analysis)
    }
}
