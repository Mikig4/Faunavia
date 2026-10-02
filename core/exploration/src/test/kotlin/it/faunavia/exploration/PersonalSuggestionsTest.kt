package it.faunavia.exploration

import it.faunavia.domain.*
import it.faunavia.plausibility.ObservabilityGuidance
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PersonalSuggestionsTest {
    private val source = Provenance("fixture", "s", "test", Instant.EPOCH, "CC0", "Faunavia tests", "synthetic", "v1")
    private val point = listOf(GeoPoint(45.5, 9.2))
    private val bounds = SuggestionBounds(45.0, 8.0, 47.0, 11.0)
    private fun candidate(id: String, name: String = "Genus $id", level: EvidenceLevel = EvidenceLevel.DOCUMENTED) =
        SuggestionCandidate(id, name, level, listOf(source))
    private fun profile(id: String, score: Double = 0.8, urban: Boolean = false) = CuratedSuggestion("Genus $id",
        SuggestionProfile(id, "pilot", listOf("forest"), urban, score, "Curator reason", source), bounds)
    private fun taxon(id: String, name: String = "Genus $id") = Taxon(id, name, null, "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES", source)

    @Test fun profilesRequireHabitatReasonSourceAndSupportedVersion() {
        val valid = profile("a").profile
        assertThrows(IllegalArgumentException::class.java) { valid.copy(habitats = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(habitats = listOf(" ")) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(reason = " ") }
        assertThrows(IllegalArgumentException::class.java) { source.copy(source = " ") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(schemaVersion = 2) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(distinctivenessScore = Double.NaN) }
        assertEquals(1, valid.schemaVersion)
        val legacy = valid.copy(schemaVersion = 0, habitats = emptyList())
        val engine = PersonalSuggestionEngine(listOf(CuratedSuggestion("Genus a", legacy, bounds)))
        assertTrue(engine.select(listOf(candidate("a")), point, SuggestionView.TYPICAL).isEmpty())
    }

    @Test fun typicalThresholdAndUrbanExclusionDoNotRestrictOtherViews() {
        val candidates = listOf(candidate("a"), candidate("b"), candidate("c"))
        val engine = PersonalSuggestionEngine(listOf(profile("a", 0.6), profile("b", 0.59), profile("c", 1.0, true)))
        assertEquals(listOf("a"), engine.select(candidates, point, SuggestionView.TYPICAL).map { it.candidate.id })
        assertEquals(3, engine.select(candidates, point, SuggestionView.NEVER_OBSERVED).size)
        assertEquals(3, engine.select(candidates, point, SuggestionView.EASIER).size)
    }

    @Test fun rankingIsStableAndKeepsMotivationAndSource() {
        val engine = PersonalSuggestionEngine(listOf(profile("z"), profile("a"), profile("c", 0.95)))
        val candidates = listOf(candidate("z"), candidate("c"), candidate("a"))
        val result = engine.select(candidates, point, SuggestionView.TYPICAL)
        assertEquals(listOf("c", "a", "z"), result.map { it.candidate.id })
        assertEquals(result, engine.select(candidates.reversed(), point, SuggestionView.TYPICAL))
        assertTrue(result.all { it.reason.isNotBlank() && it.profile!!.provenance == source })
    }

    @Test fun profilesNeverGeneratePresenceAndUnsupportedAreaStaysEmpty() {
        val engine = PersonalSuggestionEngine(listOf(profile("a")))
        assertTrue(engine.select(emptyList(), point, SuggestionView.TYPICAL).isEmpty())
        assertTrue(engine.select(listOf(candidate("a")), listOf(GeoPoint(40.0, 9.0)), SuggestionView.TYPICAL).isEmpty())
        assertTrue(engine.select(listOf(candidate("a")), emptyList(), SuggestionView.TYPICAL).isEmpty())
        assertEquals(1, engine.select(listOf(candidate("a")), emptyList(), SuggestionView.NEVER_OBSERVED).size)
    }

    @Test fun identifiedDiaryUpdatesNeverObservedAcrossProviderIdsAndAuthorities() {
        val engine = PersonalSuggestionEngine(emptyList())
        val candidates = listOf(candidate("provider:a", "Turdus merula Linnaeus, 1758"))
        val observed = taxon("gbif:a", "Turdus merula")
        assertTrue(engine.select(candidates, point, SuggestionView.NEVER_OBSERVED, listOf(observed)).isEmpty())
        assertEquals(1, engine.select(candidates, point, SuggestionView.NEVER_OBSERVED, listOf(observed.copy(scientificName = "Alcedo atthis", id = "other"))).size)
        assertEquals(1, engine.select(candidates, point, SuggestionView.NEVER_OBSERVED, emptyList()).size)
        assertTrue(engine.select(candidates, point, SuggestionView.NEVER_OBSERVED, listOf(observed.copy(status = TaxonomicStatus.SYNONYM))).isNotEmpty())
    }

    @Test fun wishRelevanceUsesAvailableEvidenceWithoutChangingItsClassification() {
        val engine = PersonalSuggestionEngine(listOf(profile("a")))
        val insufficient = candidate("a", level = EvidenceLevel.INSUFFICIENT)
        val matched = engine.select(listOf(insufficient), point, SuggestionView.WISHLIST, wishedTaxa = listOf(taxon("a"))).single()
        assertSame(insufficient, matched.candidate)
        assertEquals(EvidenceLevel.INSUFFICIENT, matched.candidate.level)
        assertTrue(engine.select(emptyList(), point, SuggestionView.WISHLIST, wishedTaxa = listOf(taxon("a"))).isEmpty())
    }

    @Test fun easeNeverUsesOccurrenceCountsHabitatOrPresenceAsProbability() {
        val engine = PersonalSuggestionEngine(listOf(profile("a", 1.0), profile("z", 0.1)))
        val z = candidate("z").copy(guidance = ObservabilityGuidance.Documented(listOf("forest"), provenance = listOf(source)))
        val result = engine.select(listOf(z, candidate("a", level = EvidenceLevel.INSUFFICIENT)), point, SuggestionView.EASIER)
        assertEquals(listOf("a", "z"), result.map { it.candidate.id })
        assertTrue(result.all { "non disponibili" in it.reason && "alfabetico" in it.reason })
        assertSame(z.guidance, result.last().candidate.guidance)
    }

    @Test fun pilotProfilesAreVersionedSourcedAndIncludeAnUrbanControl() {
        assertEquals(12, PilotSuggestionProfiles.entries.size)
        assertTrue(PilotSuggestionProfiles.entries.all { it.profile.provenance.version == PilotSuggestionProfiles.VERSION })
        assertTrue(PilotSuggestionProfiles.entries.all { it.profile.provenance.source.startsWith("https://") })
        assertTrue(PilotSuggestionProfiles.entries.any { it.profile.urbanCommon })
        assertEquals("turdus merula", scientificIdentity("Turdus  merula Linnaeus, 1758"))
        assertEquals("canis lupus italicus", scientificIdentity("Canis lupus italicus Altobello, 1921"))
    }

    @Test fun duplicateProfilesAndInvalidThresholdFailExplicitly() {
        assertThrows(IllegalArgumentException::class.java) { PersonalSuggestionEngine(listOf(profile("a"), profile("a"))) }
        assertThrows(IllegalArgumentException::class.java) { PersonalSuggestionEngine(emptyList(), -1.0) }
        assertThrows(IllegalArgumentException::class.java) { candidate("a").copy(provenance = emptyList()) }
    }
}
