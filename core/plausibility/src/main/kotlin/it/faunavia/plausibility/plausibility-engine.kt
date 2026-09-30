package it.faunavia.plausibility

import it.faunavia.domain.EvidenceLevel
import it.faunavia.domain.Provenance
import it.faunavia.occurrence.DocumentedOccurrence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DocumentedOccurrencePolicy(
    val maximumAgeDays: Long = 3_650L,
    val maximumCoordinateUncertaintyMeters: Double = 20_000.0,
    val fallbackSeasonMaximumAgeDays: Long = 1_825L,
) {
    init {
        require(maximumAgeDays >= 0L && fallbackSeasonMaximumAgeDays >= 0L)
        require(maximumCoordinateUncertaintyMeters.isFinite() && maximumCoordinateUncertaintyMeters >= 0.0)
    }
}

/**
 * Resolves a species–habitat matrix against raw land-cover classes without inventing a CLCplus to
 * MAES translation. This function is deterministic and safe to call after a provider result.
 */
class HabitatCompatibilityResolver {
    fun resolve(
        taxon: TaxonReference,
        associations: List<HabitatAssociation>,
        samples: List<LandCoverSample>,
        crosswalk: CuratedHabitatCrosswalk?,
    ): HabitatAssessment {
        val sampleSources = samples.map(LandCoverSample::provenance)
        val matchingAssociations = associations.filter { it.taxon.matches(taxon.id, taxon.scientificName) }
        val associationSources = matchingAssociations.map(HabitatAssociation::provenance)
        val sources = (sampleSources + associationSources + listOfNotNull(crosswalk?.provenance)).distinctBySource()
        if (samples.isEmpty() || matchingAssociations.isEmpty() || crosswalk == null) {
            return HabitatAssessment.unavailable(sources)
        }
        if (!crosswalk.scientificallyCurated || samples.any { !it.equivalentToRequiredProduct }) {
            return HabitatAssessment.unavailable(sources)
        }
        val observedEcosystems = samples.flatMap { sample -> crosswalk.mappings[sample.rawClass].orEmpty() }.toSet()
        if (observedEcosystems.isEmpty()) return HabitatAssessment.unavailable(sources)
        val compatible = matchingAssociations
            .filter { it.strength != HabitatAssociationStrength.OCCASIONAL }
            .map(HabitatAssociation::ecosystem)
            .filter(observedEcosystems::contains)
            .toSet()
        return HabitatAssessment(
            compatibility = if (compatible.isEmpty()) Compatibility.UNSUPPORTED else Compatibility.SUPPORTED,
            matchedEcosystems = compatible,
            provenance = sources,
        )
    }
}

/**
 * Applies the F7 evidence rule. Direct documented occurrences win only when recent and usable;
 * otherwise both range and habitat are mandatory. Season changes only the confidence modifier.
 */
class PlausibilityEngine(
    private val occurrencePolicy: DocumentedOccurrencePolicy = DocumentedOccurrencePolicy(),
) {
    fun assess(request: PlausibilityRequest): PlausibilityAssessment {
        val usableOccurrences = request.occurrences.filter { isUsableOccurrence(it, request.taxon, request.evaluatedOn) }
        val range = request.range?.takeIf { it.taxon.matches(request.taxon.id, request.taxon.scientificName) }
        val rangeCompatibility = range?.compatibility ?: Compatibility.UNAVAILABLE
        val season = seasonAssessment(request)
        val evidenceLevel = when {
            usableOccurrences.isNotEmpty() -> EvidenceLevel.DOCUMENTED
            rangeCompatibility == Compatibility.SUPPORTED && request.habitat.compatibility == Compatibility.SUPPORTED ->
                EvidenceLevel.PLAUSIBLE
            else -> EvidenceLevel.INSUFFICIENT
        }
        val modifier = confidenceModifier(evidenceLevel, season.compatibility)
        val naturaContext = request.natura2000Context?.takeIf { context ->
            context.explicitlyDocumentedTaxa.any { it.matches(request.taxon.id, request.taxon.scientificName) }
        }
        val explanation = listOf(
            PlausibilityExplanationStep(
                PlausibilityStepKind.DIRECT_OCCURRENCE,
                if (usableOccurrences.isEmpty()) "no-usable-direct-occurrence" else "usable-direct-occurrence",
                usableOccurrences.map(DocumentedOccurrence::provenance),
            ),
            PlausibilityExplanationStep(
                PlausibilityStepKind.RANGE,
                "range-${rangeCompatibility.name.lowercase()}",
                listOfNotNull(range?.provenance),
            ),
            PlausibilityExplanationStep(
                PlausibilityStepKind.HABITAT,
                "habitat-${request.habitat.compatibility.name.lowercase()}",
                request.habitat.provenance,
            ),
            PlausibilityExplanationStep(
                PlausibilityStepKind.SEASON,
                "season-${season.compatibility.name.lowercase()}-${season.quality.name.lowercase()}",
                season.provenance,
            ),
            PlausibilityExplanationStep(
                PlausibilityStepKind.NATURA2000_CONTEXT,
                if (naturaContext == null) "natura2000-no-positive-context" else "natura2000-positive-context",
                listOfNotNull(naturaContext?.provenance),
            ),
            observabilityStep(request.observability),
        )
        return PlausibilityAssessment(
            level = evidenceLevel,
            confidenceModifier = modifier,
            period = request.period,
            season = season,
            explanation = explanation,
            provenance = explanation.flatMap(PlausibilityExplanationStep::provenance).distinctBySource(),
            observability = request.observability,
        )
    }

    private fun isUsableOccurrence(
        occurrence: DocumentedOccurrence,
        taxon: TaxonReference,
        evaluatedOn: LocalDate,
    ): Boolean {
        if (!taxon.matches(occurrence.taxonId, occurrence.scientificName) || occurrence.location == null) return false
        val observedOn = occurrence.observedOn.toExactLocalDateOrNull() ?: return false
        val age = ChronoUnit.DAYS.between(observedOn, evaluatedOn)
        if (age !in 0..occurrencePolicy.maximumAgeDays) return false
        return occurrence.coordinateUncertaintyMeters?.let { it <= occurrencePolicy.maximumCoordinateUncertaintyMeters } ?: true
    }

    private fun seasonAssessment(request: PlausibilityRequest): SeasonAssessment {
        val institutional = request.institutionalSeasons.filter {
            it.quality == SeasonDataQuality.INSTITUTIONAL && it.taxon.matches(request.taxon.id, request.taxon.scientificName)
        }
        if (institutional.isNotEmpty()) {
            val activeMonths = institutional.flatMap(SeasonWindow::activeMonths).toSet()
            return seasonFromMonths(request.period, activeMonths, SeasonDataQuality.INSTITUTIONAL, institutional.flatMap(SeasonWindow::provenance))
        }
        val derivedMonths = request.occurrences.filter { occurrence ->
            request.taxon.matches(occurrence.taxonId, occurrence.scientificName) &&
                occurrence.observedOn.toExactLocalDateOrNull()?.let { observedOn ->
                    val age = ChronoUnit.DAYS.between(observedOn, request.evaluatedOn)
                    age in 0..occurrencePolicy.fallbackSeasonMaximumAgeDays
                } == true
        }.mapNotNull { it.observedOn.toExactLocalDateOrNull()?.monthValue }.toSet()
        if (derivedMonths.isEmpty()) return SeasonAssessment(SeasonCompatibility.UNKNOWN, SeasonDataQuality.UNAVAILABLE)
        val sources = request.occurrences.filter { occurrence ->
            request.taxon.matches(occurrence.taxonId, occurrence.scientificName) &&
                occurrence.observedOn.toExactLocalDateOrNull()?.monthValue in derivedMonths
        }.map(DocumentedOccurrence::provenance).distinctBySource()
        // Sampling absence cannot establish that a season is unsuitable, so fallback never reduces confidence.
        val compatibility = if (request.period.months().intersect(derivedMonths).isNotEmpty()) {
            SeasonCompatibility.SUPPORTS
        } else {
            SeasonCompatibility.UNKNOWN
        }
        return SeasonAssessment(compatibility, SeasonDataQuality.DERIVED_OCCURRENCES, derivedMonths, sources)
    }

    private fun seasonFromMonths(
        period: AnalysisPeriod,
        activeMonths: Set<Int>,
        quality: SeasonDataQuality,
        provenance: List<Provenance>,
    ): SeasonAssessment {
        val requested = period.months()
        val intersection = requested.intersect(activeMonths)
        val compatibility = when {
            intersection.isEmpty() -> SeasonCompatibility.REDUCES
            intersection == requested -> SeasonCompatibility.SUPPORTS
            else -> SeasonCompatibility.MIXED
        }
        return SeasonAssessment(compatibility, quality, activeMonths, provenance.distinctBySource())
    }

    private fun confidenceModifier(level: EvidenceLevel, season: SeasonCompatibility): ConfidenceModifier {
        if (level == EvidenceLevel.INSUFFICIENT) return ConfidenceModifier.NOT_APPLICABLE
        return when (season) {
            SeasonCompatibility.SUPPORTS -> ConfidenceModifier.SEASON_SUPPORTS
            SeasonCompatibility.REDUCES -> ConfidenceModifier.SEASON_REDUCES
            SeasonCompatibility.MIXED -> ConfidenceModifier.SEASON_MIXED
            SeasonCompatibility.UNKNOWN -> ConfidenceModifier.SEASON_UNKNOWN
        }
    }

    private fun observabilityStep(guidance: ObservabilityGuidance): PlausibilityExplanationStep = when (guidance) {
        ObservabilityGuidance.Unavailable -> PlausibilityExplanationStep(
            PlausibilityStepKind.OBSERVABILITY,
            "observability-unavailable",
        )
        is ObservabilityGuidance.Documented -> PlausibilityExplanationStep(
            PlausibilityStepKind.OBSERVABILITY,
            "observability-documented",
            guidance.provenance,
        )
    }
}

private fun String?.toExactLocalDateOrNull(): LocalDate? {
    val match = EXACT_ISO_DATE.matchEntire(this.orEmpty()) ?: return null
    return runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()
}

private val EXACT_ISO_DATE = Regex("^(\\d{4}-\\d{2}-\\d{2})(?:T.*)?$")

internal fun List<Provenance>.distinctBySource(): List<Provenance> = distinctBy { "${it.source}/${it.recordId}" }
