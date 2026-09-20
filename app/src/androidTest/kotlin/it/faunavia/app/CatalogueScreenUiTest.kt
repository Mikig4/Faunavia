package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.faunavia.domain.Provenance
import it.faunavia.domain.Taxon
import it.faunavia.domain.TaxonomicStatus
import it.faunavia.taxonomy.TaxonomyFailure
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchEntry
import it.faunavia.taxonomy.TaxonomySearchOrigin
import it.faunavia.taxonomy.TaxonomySearchResult
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
