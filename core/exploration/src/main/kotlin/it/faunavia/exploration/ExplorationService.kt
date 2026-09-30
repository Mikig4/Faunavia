package it.faunavia.exploration

import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteSource
import it.faunavia.occurrence.DocumentedOccurrence
import it.faunavia.occurrence.OccurrenceGateway
import it.faunavia.occurrence.OccurrenceResultOrigin
import it.faunavia.occurrence.OccurrenceSearchResult
import it.faunavia.plausibility.AnalysisPeriod
import it.faunavia.plausibility.PlausibilityAssessment
import it.faunavia.plausibility.PlausibilityEngine
import it.faunavia.plausibility.PlausibilityRequest
import it.faunavia.plausibility.TaxonReference
import it.faunavia.route.RouteAnalysis
import it.faunavia.route.RouteAnalysisConfig
import it.faunavia.route.RouteDocumentParser
import it.faunavia.route.RouteEngine
import java.time.Instant
import java.time.LocalDate

data class ExploredTaxon(
    val id: String,
    val scientificName: String,
    val occurrences: List<DocumentedOccurrence>,
    val assessment: PlausibilityAssessment,
)

sealed interface ExplorationResult {
    data class Ready(
        val analysis: RouteAnalysis,
        val taxa: List<ExploredTaxon>,
        val origin: OccurrenceResultOrigin,
        val partial: Boolean,
        val unclassifiedCount: Int,
    ) : ExplorationResult

    data class Unavailable(val analysis: RouteAnalysis) : ExplorationResult
}

/** The F8A vertical slice; nothing in this path persists a route or a trip. */
class ExplorationService(
    private val gateway: OccurrenceGateway,
    private val routeEngine: RouteEngine = RouteEngine(),
    private val parser: RouteDocumentParser = RouteDocumentParser(),
    private val plausibility: PlausibilityEngine = PlausibilityEngine(),
    private val now: () -> Instant = Instant::now,
) {
    fun point(name: String, point: GeoPoint): Route = Route(
        id = "ephemeral-location",
        name = name.trim().take(160),
        points = listOf(point),
        importedAt = now(),
        source = RouteSource.LOCATION,
    )

    fun document(fileName: String, content: String): Route = parser.parse(fileName, content, now()).route

    suspend fun explore(
        route: Route,
        period: AnalysisPeriod,
        config: RouteAnalysisConfig = RouteAnalysisConfig(),
        refresh: Boolean = false,
    ): ExplorationResult {
        val analysis = routeEngine.analyze(route, config)
        return when (val search = gateway.search(analysis, refresh)) {
            is OccurrenceSearchResult.Failure -> ExplorationResult.Unavailable(analysis)
            is OccurrenceSearchResult.Records -> ExplorationResult.Ready(
                analysis = analysis,
                taxa = buildTaxa(search.occurrences, period),
                origin = search.origin,
                partial = search.providerFailures.isNotEmpty(),
                unclassifiedCount = search.occurrences.count {
                    it.scientificName.trim().split(Regex("\\s+")).size < 2
                },
            )
        }
    }

    private fun buildTaxa(occurrences: List<DocumentedOccurrence>, period: AnalysisPeriod): List<ExploredTaxon> {
        return occurrences
            .filter { it.scientificName.trim().split(Regex("\\s+")).size >= 2 }
            .groupBy { it.taxonId ?: it.scientificName.trim().lowercase() }
            .mapNotNull { (id, evidence) ->
                val name = evidence.first().scientificName
                val reference = runCatching { TaxonReference(id, name) }.getOrNull() ?: return@mapNotNull null
                ExploredTaxon(
                    id = id,
                    scientificName = name,
                    occurrences = evidence,
                    assessment = plausibility.assess(
                        PlausibilityRequest(
                            taxon = reference,
                            period = period,
                            evaluatedOn = LocalDate.ofInstant(now(), java.time.ZoneOffset.UTC),
                            occurrences = evidence,
                        ),
                    ),
                )
            }
            .sortedWith(compareBy({ it.assessment.level.ordinal }, ExploredTaxon::scientificName))
    }
}
