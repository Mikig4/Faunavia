package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.Provenance
import it.faunavia.domain.Taxon
import it.faunavia.domain.TaxonomicStatus
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchEntry
import it.faunavia.taxonomy.TaxonomySearchOrigin
import it.faunavia.taxonomy.TaxonomySearchResult
import it.faunavia.testing.FakeClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryScreenUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<DiaryTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "diary-ui-test.db"
    private val now = Instant.parse("2026-09-18T09:15:00Z")
    private val zone = ZoneId.of("Europe/Rome")
    private lateinit var database: FaunaviaDatabase
    private lateinit var local: LocalRepositories

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(databaseName)
        database = FaunaviaDatabase.open(context, databaseName)
        local = LocalRepositories(database, FakeClock(now.toEpochMilli()))
        local.catalogue.saveTaxonWithAliases(taxon, listOf("Merlo", "Merula vulgaris"))
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun speciesIsRequiredAndNoIncompleteSearchIsPersisted() {
        showDiary()

        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("me")
        composeRule.onNodeWithTag("diary-save").performClick()

        composeRule.onNodeWithTag("diary-editor-error").assertIsDisplayed().assertTextContains("Scegli una specie")
        assertFalse(runBlocking { local.diary.list().isNotEmpty() })
    }

    @Test fun createsEditsAndDeletesAStoredTaxonWithoutAnySuggestion() {
        showDiary()

        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("merlo")
        composeRule.onNodeWithTag("diary-taxon-result-gbif:2490719").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("diary-quantity").performTextReplacement("2")
        composeRule.onNodeWithTag("diary-notes").performTextInput("Due merli sul prato")
        composeRule.onNodeWithTag("diary-save").performClick()

        waitFor { local.diary.get("ui-observation") != null }
        composeRule.onNodeWithTag("diary-entry-ui-observation").assertIsDisplayed()
        composeRule.onNodeWithTag("diary-edit-ui-observation").performClick()
        composeRule.onNodeWithTag("diary-notes").performTextReplacement("Osservazione corretta")
        composeRule.onNodeWithTag("diary-save").performClick()

        waitFor { local.diary.get("ui-observation")?.notes == "Osservazione corretta" }
        composeRule.onNodeWithTag("diary-delete-ui-observation").performClick()
        composeRule.onNodeWithTag("diary-delete-confirm-ui-observation").assertIsDisplayed().performClick()
        waitFor { local.diary.get("ui-observation") == null }
        composeRule.onNodeWithTag("diary-empty").assertIsDisplayed()
    }

    private fun showDiary() {
        composeRule.setContent {
            FaunaviaTheme {
                DiaryScreen(
                    diary = local.diary,
                    catalogue = local.catalogue,
                    taxonomySearch = OfflineTaxonomySearch(listOf(entry)),
                    deviceZone = zone,
                    now = { now },
                    idFactory = { "ui-observation" },
                )
            }
        }
    }

    private fun waitFor(condition: suspend () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { runBlocking { condition() } }
    }

    private class OfflineTaxonomySearch(
        private val entries: List<TaxonomySearchEntry>,
    ) : TaxonomySearch {
        override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
            check(!online) { "The diary must never need an online taxon lookup." }
            val matches = entries.filter { entry ->
                listOf(entry.taxon.scientificName, entry.taxon.commonName.orEmpty(), *entry.aliases.toTypedArray())
                    .any { name -> name.contains(query, ignoreCase = true) }
            }
            return if (matches.isEmpty()) TaxonomySearchResult.Empty(TaxonomySearchOrigin.OFFLINE_SELECTED)
            else TaxonomySearchResult.Results(matches, TaxonomySearchOrigin.OFFLINE_SELECTED)
        }

        override suspend fun select(entry: TaxonomySearchEntry) = Unit
    }

    private companion object {
        val taxon = Taxon(
            "gbif:2490719", "Turdus merula", "Merlo", "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES",
            Provenance(
                "fixture", "2490719", "test", Instant.parse("2026-09-18T09:15:00Z"),
                "CC0", "Faunavia test", "test", "v1",
            ),
        )
        val entry = TaxonomySearchEntry(taxon, listOf("Merlo", "Merula vulgaris"))
    }
}
