package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.faunavia.domain.Provenance
import it.faunavia.domain.AppClock
import it.faunavia.domain.Taxon
import it.faunavia.domain.TaxonomicStatus
import it.faunavia.taxonomy.TaxonomyFailure
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchEntry
import it.faunavia.taxonomy.TaxonomySearchOrigin
import it.faunavia.taxonomy.TaxonomySearchResult
import it.faunavia.taxonomy.GbifHttpClient
import it.faunavia.taxonomy.GbifTaxonomyProvider
import it.faunavia.taxonomy.TaxonSelectionStore
import it.faunavia.taxonomy.StoredTaxon
import it.faunavia.taxonomy.TaxonomySearchService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueScreenUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CatalogueTestActivity>()

    @Test fun showsLoadingDuringDebounce() {
        val search = FakeSearch(TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE))
        show(search, debounceMillis = 5_000)

        composeRule.onNodeWithTag("catalogue-query").performTextInput("merlo")

        composeRule.onNodeWithTag("catalogue-loading").assertIsDisplayed()
        assertEquals(emptyList<String>(), search.queries)
    }

    @Test fun showsEmptyStateForAnAcceptedAnimaliaSearchWithoutResults() {
        show(FakeSearch(TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE)))

        composeRule.onNodeWithTag("catalogue-query").performTextInput("merlo")

        composeRule.onNodeWithTag("catalogue-empty").assertIsDisplayed()
    }

    @Test fun showsRecoverableErrorAndCachedSelectedTaxon() {
        show(FakeSearch(TaxonomySearchResult.Failure(TaxonomyFailure.TIMEOUT, listOf(entry))))

        composeRule.onNodeWithTag("catalogue-query").performTextInput("merlo")

        composeRule.onNodeWithTag("catalogue-error").assertIsDisplayed()
        composeRule.onNodeWithTag("catalogue-result-gbif:2490719").assertIsDisplayed()
    }

    @Test fun commonNameLookupThroughTheRealAdapterCanBeSelectedAndReadOffline() {
        val stored = mutableListOf<StoredTaxon>()
        val store = object : TaxonSelectionStore {
            override suspend fun searchSelected(query: String, limit: Int): List<StoredTaxon> = stored.take(limit)
            override suspend fun saveSelected(taxon: Taxon, aliases: List<String>) { stored += StoredTaxon(taxon, aliases) }
        }
        val clock = object : AppClock { override fun nowEpochMillis(): Long = 1_789_729_200_000L }
        val http = object : GbifHttpClient {
            override fun get(url: String): String {
                check(url.contains("qField=VERNACULAR"))
                return """{"results":[{"key":2490719,"scientificName":"Turdus merula Linnaeus, 1758",
                    "canonicalName":"Turdus merula","taxonomicStatus":"ACCEPTED","rank":"SPECIES","kingdom":"Animalia",
                    "vernacularNames":[{"vernacularName":"Merlo","language":"ita"}]}]}"""
            }
        }
        val search = TaxonomySearchService(store, GbifTaxonomyProvider(http, clock), clock)
        show(search)
        composeRule.onNodeWithTag("catalogue-query").performTextInput("merlo")
        closeSoftKeyboard()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("catalogue-result-gbif:2490719").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("catalogue-result-gbif:2490719").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("catalogue-selected").fetchSemanticsNodes().isNotEmpty()
        }
        val offline = runBlocking { search.search("merlo", online = false) } as TaxonomySearchResult.Results
        assertEquals("Merlo", offline.entries.single().taxon.commonName)
        assertEquals(TaxonomySearchOrigin.OFFLINE_SELECTED, offline.origin)
    }

    @Test fun unexpectedLookupErrorStopsLoadingAndCanRetry() {
        val calls = AtomicInteger()
        show(object : TaxonomySearch {
            override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
                if (calls.incrementAndGet() == 1) error("local lookup failed")
                return TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE)
            }
            override suspend fun select(entry: TaxonomySearchEntry) = Unit
        })
        composeRule.onNodeWithTag("catalogue-query").performTextInput("merlo")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("catalogue-error").assertIsDisplayed()
        composeRule.onNodeWithTag("catalogue-loading").assertDoesNotExist()
        composeRule.onNodeWithTag("catalogue-retry").performClick()
        composeRule.onNodeWithTag("catalogue-empty").assertIsDisplayed()
        assertEquals(2, calls.get())
    }

    @Test fun completionOfAnObsoleteLookupDoesNotHideTheCurrentLoadingState() {
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val completed = AtomicInteger()
        show(object : TaxonomySearch {
            override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
                calls.incrementAndGet()
                if (query == "mer") withContext(NonCancellable) { first.await() } else second.await()
                completed.incrementAndGet()
                return TaxonomySearchResult.Empty(TaxonomySearchOrigin.LIVE)
            }
            override suspend fun select(entry: TaxonomySearchEntry) = Unit
        })
        composeRule.onNodeWithTag("catalogue-query").performTextInput("mer")
        composeRule.waitUntil(5_000) { calls.get() == 1 }
        composeRule.onNodeWithTag("catalogue-query").performTextInput("lo")
        composeRule.waitUntil(5_000) { calls.get() == 2 }
        first.complete(Unit)
        composeRule.waitUntil(5_000) { completed.get() == 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalogue-loading").assertIsDisplayed()
        second.complete(Unit)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("catalogue-empty").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("catalogue-loading").assertDoesNotExist()
    }

    private fun show(search: TaxonomySearch, debounceMillis: Long = 0) {
        composeRule.setContent {
            FaunaviaTheme { CatalogueScreen(search, debounceMillis) }
        }
    }

    private class FakeSearch(
        private val response: TaxonomySearchResult,
    ) : TaxonomySearch {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
            queries += query
            return response
        }
        override suspend fun select(entry: TaxonomySearchEntry) = Unit
    }

    private companion object {
        val entry = TaxonomySearchEntry(
            taxon = Taxon(
                "gbif:2490719", "Turdus merula", "Merlo", "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES",
                Provenance(
                    "fixture", "2490719", "test", Instant.parse("2026-09-18T10:00:00Z"),
                    "CC0", "Faunavia test", "test", "v1",
                ),
            ),
            aliases = listOf("Merlo"),
        )
    }
}
