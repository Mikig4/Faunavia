package it.faunavia.plausibility

import it.faunavia.domain.EvidenceLevel
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.occurrence.DocumentedOccurrence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A selected, accepted taxon identified by a stable ID and its canonical scientific name. */
data class TaxonReference(
    val id: String,
    val scientificName: String,
) {
    init {
        require(id.isNotBlank())
        require(scientificName.trim().split(Regex("\\s+")).size >= 2) {
            "A scientific name must include at least genus and species."
        }
    }

    internal fun matches(otherId: String?, otherScientificName: String): Boolean =
        otherId == id || canonicalScientificName(scientificName) == canonicalScientificName(otherScientificName)
}

/** The requested travel/exploration interval, deliberately independent of the current date. */
data class AnalysisPeriod(
    val startsOn: LocalDate,
    val endsOn: LocalDate,
) {
    init {
        require(!endsOn.isBefore(startsOn))
        require(ChronoUnit.DAYS.between(startsOn, endsOn) <= MAX_ANALYSIS_PERIOD_DAYS) {
            "An analysis period is limited to one year."
        }
    }

    fun months(): Set<Int> = buildSet {
        var cursor = startsOn.withDayOfMonth(1)
        val last = endsOn.withDayOfMonth(1)
        while (!cursor.isAfter(last)) {
            add(cursor.monthValue)
            cursor = cursor.plusMonths(1)
        }
    }
}

const val MAX_ANALYSIS_PERIOD_DAYS = 366L

enum class Compatibility {
    SUPPORTED,
    UNSUPPORTED,
    UNAVAILABLE,
}

/** Adjacent reporting ranges do not satisfy the corridor-intersection requirement. */
enum class RangeRelation {
    INTERSECTS,
    ADJACENT,
    OUTSIDE,
}

data class RangeEvidence(
    val taxon: TaxonReference,
    val relation: RangeRelation,
    val provenance: Provenance,
) {
    val compatibility: Compatibility
        get() = if (relation == RangeRelation.INTERSECTS) Compatibility.SUPPORTED else Compatibility.UNSUPPORTED
}

enum class HabitatAssociationStrength {
    PREFERRED,
    SUITABLE,
    OCCASIONAL,
}

/** A MAES/EUNIS association. Occasional associations are never enough to promote plausibility. */
data class HabitatAssociation(
    val taxon: TaxonReference,
    val ecosystem: String,
    val strength: HabitatAssociationStrength,
    val provenance: Provenance,
) {
    init { require(ecosystem.isNotBlank()) }
}

/** A raw, unaltered CLCplus value. It is not itself a MAES ecosystem. */
data class LandCoverSample(
    val location: GeoPoint,
    val rawClass: String,
    val productVersion: String,
    val equivalentToRequiredProduct: Boolean,
    val provenance: Provenance,
) {
    init {
        require(rawClass.isNotBlank() && productVersion.isNotBlank())
    }
}

/**
 * The only permitted land-cover translation boundary. A translation must be explicitly curated;
 * CLCplus classes are never assumed to be MAES ecosystems by name.
 */
data class CuratedHabitatCrosswalk(
    val mappings: Map<String, Set<String>>,
    val scientificallyCurated: Boolean,
    val provenance: Provenance,
) {
    init {
        require(mappings.keys.none { it.isBlank() })
        require(mappings.values.flatten().none { it.isBlank() })
    }
}

data class HabitatAssessment(
    val compatibility: Compatibility,
    val matchedEcosystems: Set<String> = emptySet(),
    val provenance: List<Provenance> = emptyList(),
) {
    init {
        require(compatibility != Compatibility.SUPPORTED || matchedEcosystems.isNotEmpty())
    }

    companion object {
        fun unavailable(provenance: List<Provenance> = emptyList()): HabitatAssessment = HabitatAssessment(
            compatibility = Compatibility.UNAVAILABLE,
            provenance = provenance,
        )
    }
}

enum class SeasonDataQuality {
    INSTITUTIONAL,
    DERIVED_OCCURRENCES,
    UNAVAILABLE,
}

enum class SeasonCompatibility {
    SUPPORTS,
    REDUCES,
    MIXED,
    UNKNOWN,
}

/** Explicit monthly coverage from a documented source; bare Article 12 codes are not months. */
data class SeasonWindow(
    val taxon: TaxonReference,
    val activeMonths: Set<Int>,
    val quality: SeasonDataQuality,
    val provenance: List<Provenance>,
) {
    init {
        require(activeMonths.isNotEmpty() && activeMonths.all { it in 1..12 })
        require(quality != SeasonDataQuality.UNAVAILABLE)
        require(provenance.isNotEmpty())
    }
}

data class SeasonAssessment(
    val compatibility: SeasonCompatibility,
    val quality: SeasonDataQuality,
    val activeMonths: Set<Int> = emptySet(),
    val provenance: List<Provenance> = emptyList(),
)

/** Observability advice remains separate from the evidence level and is displayed only if sourced. */
sealed interface ObservabilityGuidance {
    data object Unavailable : ObservabilityGuidance

    data class Documented(
        val habitatsToLookFor: List<String> = emptyList(),
        val documentedPeriod: String? = null,
        val documentedTimeOfDay: String? = null,
        val provenance: List<Provenance>,
    ) : ObservabilityGuidance {
        init {
            require(habitatsToLookFor.isNotEmpty() || documentedPeriod != null || documentedTimeOfDay != null)
            require(provenance.isNotEmpty())
        }
    }
}

/** Natura 2000 can add positive context, but an absent context is never a negative signal. */
data class Natura2000Context(
    val siteIds: Set<String>,
    val explicitlyDocumentedTaxa: Set<TaxonReference>,
    val provenance: Provenance,
) {
    init {
        require(siteIds.isNotEmpty() && siteIds.none { it.isBlank() })
    }
}

enum class PlausibilityStepKind {
    DIRECT_OCCURRENCE,
    RANGE,
    HABITAT,
    SEASON,
    NATURA2000_CONTEXT,
    OBSERVABILITY,
}

data class PlausibilityExplanationStep(
    val kind: PlausibilityStepKind,
    val conclusion: String,
    val provenance: List<Provenance> = emptyList(),
) {
    init { require(conclusion.isNotBlank()) }
}

enum class ConfidenceModifier {
    NONE,
    SEASON_SUPPORTS,
    SEASON_REDUCES,
    SEASON_MIXED,
    SEASON_UNKNOWN,
    NOT_APPLICABLE,
}

data class PlausibilityAssessment(
    val level: EvidenceLevel,
    val confidenceModifier: ConfidenceModifier,
    val period: AnalysisPeriod,
    val season: SeasonAssessment,
    val explanation: List<PlausibilityExplanationStep>,
    val provenance: List<Provenance>,
    val observability: ObservabilityGuidance,
) {
    init { require(explanation.isNotEmpty()) }
}

data class PlausibilityRequest(
    val taxon: TaxonReference,
    val period: AnalysisPeriod,
    /** The current/evaluation date used only to judge recency of a direct historical record. */
    val evaluatedOn: LocalDate,
    val occurrences: List<DocumentedOccurrence>,
    val range: RangeEvidence? = null,
    val habitat: HabitatAssessment = HabitatAssessment.unavailable(),
    val institutionalSeasons: List<SeasonWindow> = emptyList(),
    val natura2000Context: Natura2000Context? = null,
    val observability: ObservabilityGuidance = ObservabilityGuidance.Unavailable,
)

internal fun canonicalScientificName(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .take(2)
    .joinToString(" ")
    .lowercase(Locale.ROOT)
