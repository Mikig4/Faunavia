package it.faunavia.plausibility

import it.faunavia.domain.EvidenceLevel
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.occurrence.DocumentedOccurrence
import it.faunavia.occurrence.OccurrenceProviderId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlausibilityEngineTest {
    private val taxon = TaxonReference("gbif:1929270", "Lycaena dispar (Haworth, 1802)")
    private val engine = PlausibilityEngine()

    @Test
    fun rangeHabitatAndSeasonMatrixRequiresBothMandatorySignals() {
        listOf(true, false).forEach { rangeSupported ->
            listOf(true, false).forEach { habitatSupported ->
                listOf(true, false).forEach { seasonSupported ->
                    val assessment = engine.assess(
                        request(
                            range = range(rangeSupported),
                            habitat = habitat(habitatSupported),
                            institutionalSeasons = listOf(seasonWindow(if (seasonSupported) setOf(6) else setOf(12))),
                        ),
                    )
                    val expectedLevel = if (rangeSupported && habitatSupported) {
                        EvidenceLevel.PLAUSIBLE
                    } else {
                        EvidenceLevel.INSUFFICIENT
                    }
                    assertEquals("range=$rangeSupported habitat=$habitatSupported season=$seasonSupported", expectedLevel, assessment.level)
                    val expectedModifier = when {
                        expectedLevel == EvidenceLevel.INSUFFICIENT -> ConfidenceModifier.NOT_APPLICABLE
                        seasonSupported -> ConfidenceModifier.SEASON_SUPPORTS
                        else -> ConfidenceModifier.SEASON_REDUCES
                    }
                    assertEquals(expectedModifier, assessment.confidenceModifier)
                }
            }
        }
    }

    @Test
    fun missingRangeOrHabitatSourceCannotPromotePlausibility() {
        val noRange = engine.assess(request(range = null, habitat = habitat(true)))
        val noHabitat = engine.assess(request(range = range(true), habitat = HabitatAssessment.unavailable()))

        assertEquals(EvidenceLevel.INSUFFICIENT, noRange.level)
        assertEquals(EvidenceLevel.INSUFFICIENT, noHabitat.level)
        assertTrue(noRange.explanation.any { it.conclusion == "range-unavailable" })
        assertTrue(noHabitat.explanation.any { it.conclusion == "habitat-unavailable" })
    }

    @Test
    fun adjacentRangeIsNotAnIntersection() {
        val assessment = engine.assess(request(range = range(false, RangeRelation.ADJACENT), habitat = habitat(true)))

        assertEquals(EvidenceLevel.INSUFFICIENT, assessment.level)
        assertTrue(assessment.explanation.any { it.conclusion == "range-unsupported" })
    }

    @Test
    fun travelPeriodChangesSeasonModifierButNotEvidenceLevel() {
        val season = seasonWindow(setOf(4, 5, 6, 7, 8))
        val spring = engine.assess(request(period = AnalysisPeriod(date("2026-06-10"), date("2026-06-20")), institutionalSeasons = listOf(season)))
        val winter = engine.assess(request(period = AnalysisPeriod(date("2026-12-10"), date("2026-12-20")), institutionalSeasons = listOf(season)))

        assertEquals(EvidenceLevel.PLAUSIBLE, spring.level)
        assertEquals(EvidenceLevel.PLAUSIBLE, winter.level)
        assertEquals(ConfidenceModifier.SEASON_SUPPORTS, spring.confidenceModifier)
        assertEquals(ConfidenceModifier.SEASON_REDUCES, winter.confidenceModifier)
        assertEquals(SeasonDataQuality.INSTITUTIONAL, spring.season.quality)
    }

    @Test
    fun oldOccurrenceIsHistoricalNotDocumentedAndCannotCreateFallbackSeason() {
        val old = occurrence("old", "2010-06-15")
        val assessment = engine.assess(request(occurrences = listOf(old)))

        assertEquals(EvidenceLevel.PLAUSIBLE, assessment.level)
        assertEquals(SeasonDataQuality.UNAVAILABLE, assessment.season.quality)
        assertEquals(ConfidenceModifier.SEASON_UNKNOWN, assessment.confidenceModifier)
        assertTrue(assessment.explanation.first().conclusion == "no-usable-direct-occurrence")
    }

    @Test
    fun validDocumentedOccurrenceRemainsDocumentedWhenInstitutionalSeasonReducesConfidence() {
        val assessment = engine.assess(
            request(
                occurrences = listOf(occurrence("recent", "2026-01-15")),
                institutionalSeasons = listOf(seasonWindow(setOf(12))),
            ),
        )

        assertEquals(EvidenceLevel.DOCUMENTED, assessment.level)
        assertEquals(ConfidenceModifier.SEASON_REDUCES, assessment.confidenceModifier)
    }

    @Test
    fun occurrenceMonthsAreLowerQualityFallbackOnlyWhenInstitutionalMonthsAreMissing() {
        val juneOccurrence = occurrence("june", "2026-06-15")
        val fallback = engine.assess(request(occurrences = listOf(juneOccurrence)))
        val authoritative = engine.assess(
            request(
                occurrences = listOf(juneOccurrence),
                institutionalSeasons = listOf(seasonWindow(setOf(12))),
            ),
        )

        assertEquals(SeasonDataQuality.DERIVED_OCCURRENCES, fallback.season.quality)
        assertEquals(ConfidenceModifier.SEASON_SUPPORTS, fallback.confidenceModifier)
        assertEquals(SeasonDataQuality.INSTITUTIONAL, authoritative.season.quality)
        assertEquals(ConfidenceModifier.SEASON_REDUCES, authoritative.confidenceModifier)
    }

    @Test
    fun missingObservabilityAndNatura2000DoNotAlterEvidence() {
        val withoutContext = engine.assess(request())
        val withPositiveContext = engine.assess(
            request(
                natura2000Context = Natura2000Context(setOf("IT2050001"), setOf(taxon), provenance("natura")),
                observability = ObservabilityGuidance.Documented(
                    habitatsToLookFor = listOf("wetlands"),
                    documentedTimeOfDay = "dusk",
                    provenance = listOf(provenance("guide")),
                ),
            ),
        )

        assertEquals(EvidenceLevel.PLAUSIBLE, withoutContext.level)
        assertEquals(withoutContext.level, withPositiveContext.level)
        assertEquals(ObservabilityGuidance.Unavailable, withoutContext.observability)
        assertTrue(withoutContext.explanation.any { it.conclusion == "natura2000-no-positive-context" })
        assertTrue(withPositiveContext.explanation.any { it.conclusion == "natura2000-positive-context" })
        assertTrue(withPositiveContext.explanation.any { it.conclusion == "observability-documented" })
    }

    @Test
    fun habitatResolverNeedsCuratedCrosswalkAndUsesMixedLandCoverSafely() {
        val association = HabitatAssociation(taxon, "wetlands", HabitatAssociationStrength.PREFERRED, provenance("matrix"))
        val samples = listOf(landCover("10"), landCover("6"))
        val crosswalk = CuratedHabitatCrosswalk(
            mappings = mapOf("10" to setOf("wetlands"), "6" to setOf("grassland")),
            scientificallyCurated = true,
            provenance = provenance("crosswalk"),
        )
        val resolver = HabitatCompatibilityResolver()

        assertEquals(Compatibility.SUPPORTED, resolver.resolve(taxon, listOf(association), samples, crosswalk).compatibility)
        assertEquals(
            Compatibility.UNAVAILABLE,
            resolver.resolve(taxon, listOf(association), samples, crosswalk.copy(scientificallyCurated = false)).compatibility,
        )
        assertEquals(
            Compatibility.UNSUPPORTED,
            resolver.resolve(taxon, listOf(association), listOf(landCover("6")), crosswalk).compatibility,
        )
    }

    @Test
    fun sameInputProducesSameClassificationAndTraceableExplanation() {
        val input = request(occurrences = listOf(occurrence("recent", "2026-06-15")))

        val first = engine.assess(input)
        val second = engine.assess(input)

        assertEquals(first, second)
        assertEquals(PlausibilityStepKind.entries.toSet(), first.explanation.map(PlausibilityExplanationStep::kind).toSet())
        assertFalse(first.provenance.isEmpty())
        assertTrue(first.provenance.all { it.license.isNotBlank() && it.quality.isNotBlank() })
    }

    private fun request(
        period: AnalysisPeriod = AnalysisPeriod(date("2026-06-10"), date("2026-06-20")),
        occurrences: List<DocumentedOccurrence> = emptyList(),
        range: RangeEvidence? = range(true),
        habitat: HabitatAssessment = habitat(true),
        institutionalSeasons: List<SeasonWindow> = emptyList(),
        natura2000Context: Natura2000Context? = null,
        observability: ObservabilityGuidance = ObservabilityGuidance.Unavailable,
    ): PlausibilityRequest = PlausibilityRequest(
        taxon = taxon,
        period = period,
        evaluatedOn = date("2026-09-20"),
        occurrences = occurrences,
        range = range,
        habitat = habitat,
        institutionalSeasons = institutionalSeasons,
        natura2000Context = natura2000Context,
        observability = observability,
    )

    private fun range(supported: Boolean, relation: RangeRelation = if (supported) RangeRelation.INTERSECTS else RangeRelation.OUTSIDE): RangeEvidence =
        RangeEvidence(taxon, relation, provenance("range"))

    private fun habitat(supported: Boolean): HabitatAssessment = if (supported) {
        HabitatAssessment(Compatibility.SUPPORTED, setOf("wetlands"), listOf(provenance("habitat")))
    } else {
        HabitatAssessment(Compatibility.UNSUPPORTED, provenance = listOf(provenance("habitat")))
    }

    private fun seasonWindow(months: Set<Int>): SeasonWindow =
        SeasonWindow(taxon, months, SeasonDataQuality.INSTITUTIONAL, listOf(provenance("season")))

    private fun occurrence(id: String, observedOn: String): DocumentedOccurrence = DocumentedOccurrence(
        id = "gbif:$id",
        provider = OccurrenceProviderId.GBIF,
        providerRecordId = id,
        taxonId = taxon.id,
        scientificName = taxon.scientificName,
        observedOn = observedOn,
        location = GeoPoint(45.5, 9.2),
        coordinateUncertaintyMeters = 100.0,
        sourceUrl = "https://example.test/$id",
        provenance = provenance("occurrence-$id"),
    )

    private fun landCover(rawClass: String): LandCoverSample = LandCoverSample(
        location = GeoPoint(45.5, 9.2),
        rawClass = rawClass,
        productVersion = "2023",
        equivalentToRequiredProduct = true,
        provenance = provenance("clcplus-$rawClass"),
    )

    private fun provenance(id: String): Provenance = Provenance(
        source = "Fixture source",
        recordId = id,
        query = "fixture query $id",
        retrievedAt = Instant.parse("2026-09-20T00:00:00Z"),
        license = "fixture license",
        attribution = "fixture attribution",
        quality = "fixture quality",
        version = "fixture-v1",
    )

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
