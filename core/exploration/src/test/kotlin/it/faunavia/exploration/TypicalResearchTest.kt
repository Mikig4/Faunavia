package it.faunavia.exploration

import it.faunavia.domain.*
import it.faunavia.plausibility.*
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TypicalResearchTest {
    private val source = Provenance("fixture", "r", "test", Instant.EPOCH, "CC0", "Faunavia", "synthetic", "v1")
    private val points = listOf(GeoPoint(45.0, 9.0))
    private fun taxon(id: String, name: String): ExploredTaxon = ExploredTaxon(id, name, emptyList(),
        PlausibilityEngine().assess(PlausibilityRequest(TaxonReference(id, name),
            AnalysisPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), LocalDate.of(2026, 10, 2), occurrences = emptyList()))
            .let { it.copy(explanation = it.explanation.map { step -> step.copy(provenance = listOf(source)) }) })

    @Test fun curatedSelectionKeepsExactEvidenceAndExcludesUrbanAndUncuratedRecords() {
        val input = listOf(taxon("merlo", "Turdus merula"), taxon("airone", "Ardea cinerea Linnaeus, 1758"),
            taxon("unknown", "Other species"), taxon("aquila", "Aquila chrysaetos"))
        val selected = typicalTaxa(input, points)
        assertEquals(listOf("aquila", "airone"), selected.map { it.id })
        assertSame(input[3], selected[0]); assertSame(input[1], selected[1])
        assertTrue(selected.all { it.assessment.level == EvidenceLevel.INSUFFICIENT })
        assertEquals(selected, typicalTaxa(input.reversed(), points))
        assertEquals(4, input.size)
    }

    @Test fun emptyAndOutsideCoverageNeverCreateOrPromoteSpecies() {
        assertTrue(typicalTaxa(emptyList(), points).isEmpty())
        val input = listOf(taxon("airone", "Ardea cinerea"))
        assertTrue(typicalTaxa(input, listOf(GeoPoint(40.0, 9.0))).isEmpty())
        assertEquals(input, typicalTaxa(input, listOf(GeoPoint(40.0, 9.0)) + points))
    }

    @Test fun everyCuratedSpeciesHasAReviewedDistinctDistributionLinkAndCommonName() {
        val entries = PilotSpeciesPresentation.entries
        assertEquals(PilotSuggestionProfiles.entries.map { scientificIdentity(it.scientificName) }.toSet(),
            entries.map { scientificIdentity(it.scientificName) }.toSet())
        assertEquals(entries.size, entries.map { it.distribution.source.source }.distinct().size)
        entries.forEach {
            val uri = URI(it.distribution.source.source)
            assertEquals("https", uri.scheme); assertEquals("commons.wikimedia.org", uri.host)
            assertTrue(uri.path.startsWith("/wiki/File:")); assertNull(uri.query)
            assertTrue(it.commonName.isNotBlank() && it.distribution.mapDate.isNotBlank())
            assertTrue(it.distribution.source.license.isNotBlank() && it.distribution.source.attribution.isNotBlank())
            assertNotEquals(source, it.distribution.source)
        }
        assertEquals("Martin pescatore", PilotSpeciesPresentation.find("Alcedo atthis Linnaeus, 1758")!!.commonName)
        assertNull(PilotSpeciesPresentation.find("Unknown species"))
    }
}
