package it.faunavia.app

import it.faunavia.plausibility.PlausibilityExplanationStep
import it.faunavia.plausibility.PlausibilityStepKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationExplanationTest {
    @Test fun directAndMissingRangeMessagesArePlainLanguageAndNotPresenceClaims() {
        val direct = explanationText(PlausibilityExplanationStep(
            PlausibilityStepKind.DIRECT_OCCURRENCE, "usable-direct-occurrence"))
        val noRange = explanationText(PlausibilityExplanationStep(PlausibilityStepKind.RANGE, "range-unavailable"))
        assertTrue(direct.contains("Osservazione diretta"))
        assertFalse(direct.contains("presente oggi"))
        assertTrue(noRange.contains("non disponibile"))
    }

    @Test fun unknownSeasonAndNaturaContextDoNotAssertAbsence() {
        val season = explanationText(PlausibilityExplanationStep(PlausibilityStepKind.SEASON,
            "season-unknown-unavailable"))
        val natura = explanationText(PlausibilityExplanationStep(PlausibilityStepKind.NATURA2000_CONTEXT,
            "natura2000-no-positive-context"))
        assertTrue(season.contains("non valutabile"))
        assertTrue(natura.contains("non è una prova negativa"))
    }
}
