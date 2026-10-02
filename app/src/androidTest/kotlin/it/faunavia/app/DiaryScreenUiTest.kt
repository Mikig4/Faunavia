package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
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
import it.faunavia.taxonomy.TaxonomySearchService
import it.faunavia.taxonomy.CatalogueTaxonSelectionStore
import it.faunavia.taxonomy.TaxonomyCandidate
import it.faunavia.taxonomy.TaxonomyProvider
import it.faunavia.taxonomy.TaxonomyProviderResult
import it.faunavia.taxonomy.TaxonomyFailure
import it.faunavia.testing.FakeClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()

        composeRule.onNodeWithTag("diary-editor-error")
            .assertIsDisplayed()
            .assertTextContains("Scegli una specie", substring = true)
        assertFalse(runBlocking { local.diary.list().isNotEmpty() })
    }

    @Test fun createsEditsAndDeletesAStoredTaxonWithoutAnySuggestion() {
        showDiary()

        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("merlo")
        waitForTaxon(taxon.id)
        composeRule.onNodeWithTag("diary-taxon-result-gbif:2490719").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("diary-quantity").performTextReplacement("2")
        composeRule.onNodeWithTag("diary-notes").performTextInput("Due merli sul prato")
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()

        waitFor { local.diary.get("ui-observation") != null }
        composeRule.onNodeWithTag("diary-entry-ui-observation").assertIsDisplayed()
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-edit-ui-observation"))
        composeRule.onNodeWithTag("diary-edit-ui-observation").assertIsDisplayed().performClick()
        scrollEditorTo("diary-notes")
        composeRule.onNodeWithTag("diary-notes").performTextReplacement("Osservazione corretta")
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()

        waitFor { local.diary.get("ui-observation")?.notes == "Osservazione corretta" }
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-delete-ui-observation"))
        composeRule.onNodeWithTag("diary-delete-ui-observation").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-delete-confirm-ui-observation"))
        composeRule.onNodeWithTag("diary-delete-confirm-ui-observation").assertIsDisplayed().performClick()
        waitFor { local.diary.get("ui-observation") == null }
        composeRule.onNodeWithTag("diary-empty").assertIsDisplayed()
    }

    @Test fun selectsANewCatalogueSpeciesDirectlyAndSavesItsLinkedObservationOffline() {
        val fox = taxon.copy(id = "gbif:fox", scientificName = "Vulpes vulpes", commonName = "Volpe",
            provenance = taxon.provenance.copy(recordId = "fox"))
        var offline = false
        var calls = 0
        val provider = object : TaxonomyProvider {
            override suspend fun suggest(query: String, limit: Int): TaxonomyProviderResult {
                calls++
                return if (offline) TaxonomyProviderResult.Failure(TaxonomyFailure.NETWORK)
                else TaxonomyProviderResult.Success(listOf(TaxonomyCandidate(fox, listOf("Canis vulpes"))))
            }
        }
        val search = TaxonomySearchService(CatalogueTaxonSelectionStore(local.catalogue), provider,
            FakeClock(now.toEpochMilli()))
        runBlocking { local.trips.save(f8bTrip()) }
        showDiary(search)
        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("Canis vulpes")
        waitForTaxon(fox.id)
        assertNull(runBlocking { local.catalogue.taxon(fox.id) })
        scrollEditorTo("diary-taxon-result-${fox.id}")
        composeRule.onNodeWithTag("diary-taxon-result-${fox.id}").performClick()
        waitFor { local.catalogue.taxon(fox.id) != null }
        offline = true
        val callsBeforeSave = calls
        scrollEditorTo("diary-link-trip-t")
        composeRule.onNodeWithTag("diary-link-trip-t").performClick()
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("ui-observation") != null }
        assertEquals("t", runBlocking { local.diary.get("ui-observation")!!.tripId })
        assertEquals(fox.id, runBlocking { local.diary.get("ui-observation")!!.taxonId })
        assertEquals(fox.provenance, runBlocking { local.catalogue.taxon(fox.id)!!.provenance })
        assertEquals(callsBeforeSave, calls)
        val cached = runBlocking { search.search("Canis vulpes", online = false) } as TaxonomySearchResult.Results
        assertEquals(fox.id, cached.entries.single().taxon.id)
    }

    @Test fun unavailableCatalogueCanRetryWithoutPersistingAnIncompleteObservation() {
        var attempts = 0
        val provider = object : TaxonomyProvider {
            override suspend fun suggest(query: String, limit: Int): TaxonomyProviderResult {
                attempts++
                return if (attempts == 1) TaxonomyProviderResult.Failure(TaxonomyFailure.NETWORK)
                else TaxonomyProviderResult.Success(listOf(TaxonomyCandidate(taxon)))
            }
        }
        showDiary(TaxonomySearchService(CatalogueTaxonSelectionStore(local.catalogue), provider,
            FakeClock(now.toEpochMilli())))
        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("merlo")
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("diary-taxon-retry").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("diary-taxon-message").assertTextContains("Non riesco a raggiungere il catalogo", substring = true)
        assertFalse(runBlocking { local.diary.list().isNotEmpty() })
        composeRule.onNodeWithTag("diary-taxon-retry").performClick()
        composeRule.waitUntil(5_000) { attempts == 2 }
        waitForTaxon(taxon.id)
        composeRule.onNodeWithTag("diary-taxon-result-${taxon.id}").performClick()
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("ui-observation") != null }
    }

    @Test fun failedSpeciesSelectionKeepsTheEditorAndAllowsAnotherSelection() {
        val fox = taxon.copy(id = "gbif:fox", scientificName = "Vulpes vulpes", commonName = "Volpe")
        val entry = TaxonomySearchEntry(fox, listOf("Volpe"))
        var attempts = 0
        showDiary(object : TaxonomySearch {
            override suspend fun search(query: String, online: Boolean) =
                TaxonomySearchResult.Results(listOf(entry), TaxonomySearchOrigin.LIVE)
            override suspend fun select(entry: TaxonomySearchEntry) {
                attempts++
                if (attempts == 1) throw IllegalStateException("fixture disk failure")
                local.catalogue.saveTaxonWithAliases(entry.taxon, entry.aliases)
            }
        })
        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("volpe")
        waitForTaxon(fox.id)
        composeRule.onNodeWithTag("diary-taxon-result-${fox.id}").performClick()
        composeRule.waitUntil(5_000) { attempts == 1 }
        scrollEditorTo("diary-taxon-query")
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("diary-taxon-message").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("diary-taxon-message").assertTextContains("Non riesco a salvare", substring = true)
        assertNull(runBlocking { local.catalogue.taxon(fox.id) })
        assertFalse(runBlocking { local.diary.list().isNotEmpty() })
        composeRule.onNodeWithTag("diary-taxon-result-${fox.id}").performClick()
        waitFor { local.catalogue.taxon(fox.id) != null }
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("ui-observation") != null }
        assertNotNull(runBlocking { local.diary.get("ui-observation") })
    }

    private fun waitForTaxon(id: String) {
        closeSoftKeyboard()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("diary-editor").performScrollToNode(hasTestTag("diary-taxon-result-$id")) }
            composeRule.onAllNodesWithTag("diary-taxon-result-$id").fetchSemanticsNodes().isNotEmpty()
        }
        scrollEditorTo("diary-taxon-result-$id")
    }

    private fun showDiary(search: TaxonomySearch = OfflineTaxonomySearch(listOf(entry))) {
        composeRule.setContent {
            FaunaviaTheme {
                DiaryScreen(
                    diary = local.diary,
                    catalogue = local.catalogue,
                    taxonomySearch = search,
                    tripRepository = local.trips,
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

    private fun scrollEditorTo(tag: String) {
        closeSoftKeyboard()
        composeRule.onNodeWithTag("diary-editor").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private class OfflineTaxonomySearch(
        private val entries: List<TaxonomySearchEntry>,
    ) : TaxonomySearch {
        override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
            val matches = entries.filter { entry ->
                listOf(entry.taxon.scientificName, entry.taxon.commonName.orEmpty(), *entry.aliases.toTypedArray())
                    .any { name -> name.contains(query, ignoreCase = true) }
            }
            return if (online) TaxonomySearchResult.Failure(TaxonomyFailure.NETWORK, matches)
            else if (matches.isEmpty()) TaxonomySearchResult.Empty(TaxonomySearchOrigin.OFFLINE_SELECTED)
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
