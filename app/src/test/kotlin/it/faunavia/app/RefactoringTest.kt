package it.faunavia.app

import it.faunavia.taxonomy.TaxonomySearchOrigin
import it.faunavia.taxonomy.TaxonomySearchResult
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RefactoringTest {
    @Test fun diaryParsingAcceptsTrimmedFieldsAndPreservesLocalTime() {
        val input = parseDiaryInput(" 2026-10-02 ", " 14:30 ", " 2 ", ZoneId.of("Europe/Rome"))
        assertEquals(Instant.parse("2026-10-02T12:30:00Z"), input.observedAt)
        assertEquals(2, input.quantity)
    }

    @Test fun diaryParsingReportsInvalidDatesTimesAndQuantities() {
        val zone = ZoneId.of("UTC")
        assertEquals("Inserisci una data valida nel formato AAAA-MM-GG.",
            assertThrows(IllegalArgumentException::class.java) { parseDiaryInput("invalid", "12:00", "1", zone) }.message)
        assertEquals("Inserisci un'ora valida nel formato HH:MM.",
            assertThrows(IllegalArgumentException::class.java) { parseDiaryInput("2026-10-02", "25:00", "1", zone) }.message)
        listOf("0", "-1", "1000001", "1.5", "").forEach { quantity ->
            assertThrows(IllegalArgumentException::class.java) { parseDiaryInput("2026-10-02", "12:00", quantity, zone) }
        }
    }

    @Test fun staleLookupCannotReplaceCurrentResultsOrStopCurrentLoading() {
        val state = TaxonomyLookupState()
        val stale = state.begin(true)
        val current = state.begin(true)
        state.complete(stale, TaxonomySearchResult.Empty(TaxonomySearchOrigin.OFFLINE_SELECTED))
        state.fail(stale)
        state.finish(stale)
        assertTrue(state.loading)
        assertFalse(state.unexpectedFailure)
        assertTrue(state.result is TaxonomySearchResult.AwaitingQuery)
        val result = TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE)
        state.complete(current, result)
        state.finish(current)
        assertEquals(result, state.result)
        assertFalse(state.loading)
    }

    @Test fun clearingOrSelectingTaxonInvalidatesAnOutstandingLookup() {
        val state = TaxonomyLookupState()
        val stale = state.begin(true)
        state.begin(false)
        state.complete(stale, TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE))
        state.fail(stale)
        state.finish(stale)
        assertTrue(state.result is TaxonomySearchResult.AwaitingQuery)
        assertFalse(state.loading)
        assertFalse(state.unexpectedFailure)
    }
}
