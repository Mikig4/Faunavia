package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.Provenance
import it.faunavia.domain.ObservationDraft
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.MAX_PERSONAL_COMMON_NAME_LENGTH
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
        scrollEditorTo("diary-quantity")
        composeRule.onNodeWithTag("diary-quantity").performTextReplacement("2")
        scrollEditorTo("diary-notes")
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

    @Test fun missingCommonNameCanBeAddedLaterFilteredAndClearedForOnlyOneObservation() {
        val unnamed = taxon.copy(commonName = null)
        runBlocking {
            local.catalogue.saveTaxonWithAliases(unnamed, listOf(unnamed.scientificName))
            local.diary.create(ObservationDraft("saved", unnamed.id, now, zone))
            local.diary.create(ObservationDraft("other", unnamed.id, now, zone))
        }
        showDiary()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-species-${taxon.id}"))
                composeRule.onNodeWithTag("diary-species-${taxon.id}").performClick()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("diary-filters-toggle").performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-edit-saved"))
                composeRule.onNodeWithTag("diary-edit-saved").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("diary-edit-saved").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("diary-editor").fetchSemanticsNodes().isNotEmpty() }
        scrollEditorTo("diary-personal-common-name")
        composeRule.onNodeWithTag("diary-personal-common-name").performTextReplacement("  Merlo del giardino  ")
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("saved")?.personalCommonName == "Merlo del giardino" }
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-common-name-saved"))
        composeRule.onNodeWithTag("diary-common-name-saved").assertTextContains("Merlo del giardino · nome personale", substring = true)
        assertEquals(unnamed, runBlocking { local.catalogue.taxon(unnamed.id) })
        assertNull(runBlocking { local.diary.get("other")!!.personalCommonName })
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-species-filter"))
        composeRule.onNodeWithTag("diary-species-filter").performTextReplacement("giardino")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-entry-saved"))
        composeRule.onNodeWithTag("diary-entry-saved").assertIsDisplayed()
        composeRule.onNodeWithTag("diary-entry-other").assertDoesNotExist()
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-species-filter"))
        composeRule.onNodeWithTag("diary-species-filter").performTextReplacement("")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-edit-saved"))
        composeRule.onNodeWithTag("diary-edit-saved").performClick()
        scrollEditorTo("diary-personal-common-name")
        composeRule.onNodeWithTag("diary-personal-common-name").assertTextContains("Merlo del giardino", substring = true)
        composeRule.onNodeWithTag("diary-personal-common-name").performTextReplacement("")
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("saved")?.personalCommonName == null }
        composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-entry-saved"))
        composeRule.onNodeWithTag("diary-common-name-saved").assertDoesNotExist()
    }

    @Test fun newPersonalCommonNameIsValidatedAndClearedWhenTheSelectedSpeciesChanges() {
        val unnamed = taxon.copy(commonName = null)
        val fox = taxon.copy(id = "gbif:fox", scientificName = "Vulpes vulpes", commonName = "Volpe")
        runBlocking {
            local.catalogue.saveTaxon(unnamed)
            local.catalogue.saveTaxon(fox)
        }
        showDiary(OfflineTaxonomySearch(listOf(TaxonomySearchEntry(unnamed, emptyList()), TaxonomySearchEntry(fox, emptyList()))))
        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-query").performTextInput("Turdus")
        waitForTaxon(unnamed.id)
        composeRule.onNodeWithTag("diary-taxon-result-${unnamed.id}").performClick()
        scrollEditorTo("diary-personal-common-name")
        composeRule.onNodeWithTag("diary-personal-common-name").performTextReplacement("x".repeat(MAX_PERSONAL_COMMON_NAME_LENGTH + 1))
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        scrollEditorTo("diary-editor-error")
        composeRule.onNodeWithTag("diary-editor-error").assertTextContains("nome comune personale", substring = true)
        assertNull(runBlocking { local.diary.get("ui-observation") })
        scrollEditorTo("diary-personal-common-name")
        composeRule.onNodeWithTag("diary-personal-common-name").performTextReplacement("Merlo manuale")
        scrollEditorTo("diary-taxon-query")
        composeRule.onNodeWithTag("diary-taxon-query").performTextReplacement("Vulpes")
        waitForTaxon(fox.id)
        composeRule.onNodeWithTag("diary-taxon-result-${fox.id}").performClick()
        scrollEditorTo("diary-personal-common-name")
        assertEquals("", composeRule.onNodeWithTag("diary-personal-common-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        composeRule.onNodeWithTag("diary-personal-common-name").performTextReplacement("Volpe personale")
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("ui-observation") != null }
        assertEquals(fox.id, runBlocking { local.diary.get("ui-observation")!!.taxonId })
        assertEquals("Volpe personale", runBlocking { local.diary.get("ui-observation")!!.personalCommonName })
        assertEquals(fox, runBlocking { local.catalogue.taxon(fox.id) })
    }

    @Test fun dailySummaryShowsThePersonalNameAndKeepsTheScientificIdentity() {
        runBlocking {
            local.catalogue.saveTaxon(taxon.copy(commonName = null))
            local.diary.create(ObservationDraft("named", taxon.id, now, zone, personalCommonName = "Merlo personale"))
        }
        val photos = MemoryPhotos((context.applicationContext as FaunaviaApplication).privatePhotos, local.diary, null)
        composeRule.setContent { FaunaviaTheme {
            DailySummaryScreen(SummaryLink(now.atZone(zone).toLocalDate(), zone), local.diary, local.catalogue, photos, onBack = {})
        } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("summary-observation-named").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("summary-content").performScrollToNode(hasTestTag("summary-observation-named"))
        composeRule.onNodeWithText("Merlo personale").assertIsDisplayed()
        composeRule.onNodeWithText("Nome comune personale").assertIsDisplayed()
        composeRule.onNodeWithText(taxon.scientificName).assertIsDisplayed()
        assertEquals(taxon.id, runBlocking { local.diary.get("named")!!.taxonId })
    }

    @Test fun speciesCardsGroupIdentitiesAndAddAnIndependentEncounterOffline() {
        val other = taxon.copy(id = "other-species", scientificName = "Turdus philomelos")
        runBlocking {
            local.catalogue.saveTaxon(other)
            local.diary.create(ObservationDraft("old", taxon.id, now.minusSeconds(86_400), zone,
                GeoPoint(45.0, 9.0), "Vecchio incontro", 4, personalCommonName = "Merlo del prato"))
            local.diary.create(ObservationDraft("recent", taxon.id, now, zone, personalCommonName = "Merlo del bosco"))
            local.diary.create(ObservationDraft("different", other.id, now, zone))
        }
        val before = runBlocking { local.diary.get("old") }
        var searches = 0
        showDiary(object : TaxonomySearch {
            override suspend fun search(query: String, online: Boolean): TaxonomySearchResult {
                searches++
                return TaxonomySearchResult.Empty(TaxonomySearchOrigin.OFFLINE_SELECTED)
            }
            override suspend fun select(entry: TaxonomySearchEntry) = Unit
        })
        scrollDiaryTo("diary-species-${taxon.id}")
        composeRule.onNodeWithTag("diary-species-count-${taxon.id}", useUnmergedTree = true).assertTextContains("2 avvistamenti", substring = true)
        scrollDiaryTo("diary-species-${other.id}")
        composeRule.onNodeWithTag("diary-species-${other.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("diary-entry-old").assertDoesNotExist()
        scrollDiaryTo("diary-new")
        captureDiary("species-list")
        scrollDiaryTo("diary-species-${taxon.id}")
        composeRule.onNodeWithTag("diary-species-${taxon.id}").performClick()
        scrollDiaryTo("diary-entry-recent")
        composeRule.onNodeWithTag("diary-entry-different").assertDoesNotExist()
        scrollDiaryTo("diary-new")
        captureDiary("species-detail")
        composeRule.onNodeWithTag("diary-new").performClick()
        composeRule.onNodeWithTag("diary-taxon-selected").assertTextContains(taxon.scientificName, substring = true)
        scrollEditorTo("diary-quantity")
        composeRule.onNodeWithTag("diary-quantity").assertTextContains("1", substring = true)
        composeRule.onNodeWithTag("diary-quantity").performTextReplacement("2")
        scrollEditorTo("diary-latitude")
        assertEquals("", composeRule.onNodeWithTag("diary-latitude").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        composeRule.onNodeWithTag("diary-latitude").performTextInput("46.0")
        scrollEditorTo("diary-longitude")
        composeRule.onNodeWithTag("diary-longitude").performTextInput("10.0")
        scrollEditorTo("diary-notes")
        assertEquals("", composeRule.onNodeWithTag("diary-notes").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        composeRule.onNodeWithTag("diary-notes").performTextInput("Nuovo luogo")
        scrollEditorTo("diary-save")
        composeRule.onNodeWithTag("diary-save").performClick()
        waitFor { local.diary.get("ui-observation") != null }
        val saved = runBlocking { local.diary.get("ui-observation")!! }
        assertEquals(taxon.id, saved.taxonId)
        assertEquals(GeoPoint(46.0, 10.0), saved.location)
        assertEquals(2, saved.quantity)
        assertEquals(now, saved.observedAt)
        assertNull(saved.personalCommonName)
        assertEquals(before, runBlocking { local.diary.get("old") })
        assertEquals(0, searches)
        scrollDiaryTo("diary-entry-ui-observation")
        scrollDiaryTo("diary-species-back")
        composeRule.onNodeWithTag("diary-species-back").performClick()
        scrollDiaryTo("diary-species-${taxon.id}")
        composeRule.onNodeWithTag("diary-species-count-${taxon.id}", useUnmergedTree = true).assertTextContains("3 avvistamenti", substring = true)
    }

    @Test fun speciesDetailRestoresAndDeletingOneEncounterKeepsTheOthers() {
        runBlocking {
            local.diary.create(ObservationDraft("first", taxon.id, now, zone))
            local.diary.create(ObservationDraft("second", taxon.id, now.minusSeconds(86_400), zone))
        }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { FaunaviaTheme {
            DiaryScreen(local.diary, local.catalogue, OfflineTaxonomySearch(listOf(entry)), deviceZone = zone)
        } }
        scrollDiaryTo("diary-species-${taxon.id}")
        composeRule.onNodeWithTag("diary-species-${taxon.id}").performClick()
        restoration.emulateSavedInstanceStateRestore()
        scrollDiaryTo("diary-species-back")
        composeRule.onNodeWithTag("diary-species-title").assertTextContains("Merlo", substring = true)
        scrollDiaryTo("diary-delete-first")
        composeRule.onNodeWithTag("diary-delete-first").performClick()
        scrollDiaryTo("diary-delete-confirm-first")
        composeRule.onNodeWithTag("diary-delete-confirm-first").performClick()
        waitFor { local.diary.get("first") == null }
        scrollDiaryTo("diary-entry-second")
        assertNotNull(runBlocking { local.diary.get("second") })
        scrollDiaryTo("diary-species-back")
        composeRule.onNodeWithTag("diary-species-back").performClick()
        scrollDiaryTo("diary-species-${taxon.id}")
        composeRule.onNodeWithTag("diary-species-count-${taxon.id}", useUnmergedTree = true).assertTextContains("1 avvistamento", substring = true)
    }

    private fun captureDiary(name: String) {
        val directory = java.io.File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"))).apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            composeRule.onNodeWithTag("screen-diary").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun scrollDiaryTo(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("diary-list").performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
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
