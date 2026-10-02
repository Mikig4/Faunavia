package it.faunavia.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.route.RouteAnalysis
import it.faunavia.testing.FakeClock
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F9UiTest {
    @get:Rule val compose = createAndroidComposeRule<RouteTestActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "f9-ui.db"
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private val candidate = SuggestionCandidate(f8bTaxon.id, f8bTaxon.scientificName, EvidenceLevel.DOCUMENTED, listOf(f8bProvenance))
    private var revision by mutableIntStateOf(0)
    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name)
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
        local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun urbanCommonIsAvailableOutsideTypicalAndWishlistDoesNotCreateObservation() {
        show()
        waitLoaded()
        scroll("suggestion-empty")
        compose.onNodeWithTag("suggestion-empty").assertExists()
        click("suggestion-view-NEVER_OBSERVED")
        scroll("suggestion-${f8bTaxon.id}")
        click("suggestion-details-${f8bTaxon.id}")
        compose.onNodeWithTag("suggestion-level-${f8bTaxon.id}").assertTextContains("DOCUMENTED", substring = true)
        click("suggestion-wish-${f8bTaxon.id}")
        compose.waitUntil(5_000) { runBlocking { local.wishlist.list().size == 1 } }
        click("suggestion-view-WISHLIST")
        click("wishlist-details-${f8bTaxon.id}")
        scroll("wishlist-relevance-${f8bTaxon.id}")
        compose.onNodeWithTag("wishlist-relevance-${f8bTaxon.id}").assertTextContains("DOCUMENTED", substring = true)
        assertTrue(runBlocking { local.diary.list().isEmpty() })
        click("wishlist-remove-${f8bTaxon.id}")
        compose.waitUntil(5_000) { runBlocking { local.wishlist.list().isEmpty() } }
    }

    @Test fun identifiedDiaryEditAndDeleteUpdateNeverObservedWhileDraftDoesNotHideIt() {
        runBlocking { local.unidentified.save(UnidentifiedInput("draft", f8bNow, ZoneId.of("Europe/Rome"), notes = "Merlo?")) }
        show(SuggestionView.NEVER_OBSERVED)
        waitLoaded(); scroll("suggestion-${f8bTaxon.id}")
        compose.onNodeWithTag("suggestion-${f8bTaxon.id}").assertExists()
        val draft = ObservationDraft("o", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome"))
        runBlocking { local.diary.create(draft) }
        reload(); waitLoaded(); scroll("suggestion-empty")
        compose.onNodeWithTag("suggestion-empty").assertExists()
        val other = f8bTaxon.copy(id = "other", scientificName = "Alcedo atthis")
        runBlocking { local.catalogue.saveTaxon(other); local.diary.update(draft.copy(taxonId = other.id)) }
        reload(); waitLoaded(); scroll("suggestion-${f8bTaxon.id}")
        compose.onNodeWithTag("suggestion-${f8bTaxon.id}").assertExists()
        runBlocking { local.diary.update(draft); local.diary.delete("o") }
        reload(); waitLoaded(); scroll("suggestion-${f8bTaxon.id}")
        compose.onNodeWithTag("suggestion-${f8bTaxon.id}").assertExists()
        assertEquals(1, runBlocking { local.unidentified.list().size })
    }

    @Test fun easeStatesMissingBasisAndSawRequiresASeparateDiaryConfirmation() {
        var seed: Pair<String, String>? = null
        compose.setContent { FaunaviaTheme { PersonalSuggestionsScreen(local.wishlist, local.catalogue, local.diary,
            F8BOfflineTaxonomy(), listOf(candidate), listOf(GeoPoint(45.0, 9.0)), initialView = SuggestionView.EASIER,
            onBack = {}, onSaw = { id, name -> seed = id to name }) } }
        waitLoaded(); scroll("suggestion-ease-unavailable")
        compose.onNodeWithTag("suggestion-ease-unavailable").assertTextContains("Ordine alfabetico", substring = true)
        click("suggestion-saw-${f8bTaxon.id}")
        assertEquals(f8bTaxon.id to f8bTaxon.scientificName, seed)
        assertTrue(runBlocking { local.diary.list().isEmpty() })
    }

    @Test fun wishlistSurvivesScreenRestorationAndNoEvidenceIsNotAbsence() {
        runBlocking { local.wishlist.add(f8bTaxon.id) }
        val tester = StateRestorationTester(compose)
        tester.setContent { FaunaviaTheme { PersonalSuggestionsScreen(local.wishlist, local.catalogue, local.diary,
            F8BOfflineTaxonomy(), initialView = SuggestionView.WISHLIST, onBack = {}) } }
        waitLoaded(); click("wishlist-details-${f8bTaxon.id}"); scroll("wishlist-relevance-${f8bTaxon.id}")
        compose.onNodeWithTag("wishlist-relevance-${f8bTaxon.id}").assertTextContains("Pertinenza non valutabile", substring = true)
        tester.emulateSavedInstanceStateRestore()
        waitLoaded(); scroll("wishlist-${f8bTaxon.id}")
        compose.onNodeWithTag("wishlist-${f8bTaxon.id}").assertExists()
        capture("wishlist")
        assertEquals(1, runBlocking { local.wishlist.list().size })
    }

    @Test fun catalogueSelectionAddsWishAndOfflineListRemovesIt() {
        compose.setContent { FaunaviaTheme { CatalogueScreen(F8BOfflineTaxonomy(), 0, local.wishlist, local.catalogue, local.diary) } }
        compose.onNodeWithTag("catalogue-query").performTextInput("merlo")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("catalogue-result-${f8bTaxon.id}").fetchSemanticsNodes().isNotEmpty() }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("catalogue-result-${f8bTaxon.id}").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("catalogue-add-wish").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("catalogue-add-wish").performClick()
        compose.waitUntil(5_000) { runBlocking { local.wishlist.list().size == 1 } }
        compose.onNodeWithTag("catalogue-wishlist").performClick()
        waitLoaded(); click("wishlist-remove-${f8bTaxon.id}")
        compose.waitUntil(5_000) { runBlocking { local.wishlist.list().isEmpty() } }
    }

    @Test fun failedWishRemovalShowsRetryAndKeepsLocalData() {
        runBlocking { local.wishlist.add(f8bTaxon.id) }
        var failRemoval = true
        val store = object : WishlistRepository by local.wishlist {
            override suspend fun remove(taxonId: String) {
                if (failRemoval) error("storage failure fixture")
                local.wishlist.remove(taxonId)
            }
        }
        compose.setContent { FaunaviaTheme { PersonalSuggestionsScreen(store, local.catalogue, local.diary,
            F8BOfflineTaxonomy(), initialView = SuggestionView.WISHLIST, onBack = {}) } }
        waitLoaded(); click("wishlist-remove-${f8bTaxon.id}")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("suggestion-error").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { local.wishlist.list().size })
        failRemoval = false
        compose.onNodeWithText("Riprova lettura").performClick()
        waitLoaded(); click("wishlist-remove-${f8bTaxon.id}")
        compose.waitUntil(5_000) { runBlocking { local.wishlist.list().isEmpty() } }
    }

    @Test fun typicalSpeciesShowsCuratorialReasonSourceAndUnchangedInsufficientEvidence() {
        val kingfisher = f8bTaxon.copy(id = "gbif:kingfisher", scientificName = "Alcedo atthis", commonName = "Martin pescatore")
        runBlocking { local.catalogue.saveTaxon(kingfisher) }
        val evidence = candidate.copy(id = kingfisher.id, scientificName = kingfisher.scientificName, level = EvidenceLevel.INSUFFICIENT)
        compose.setContent { FaunaviaTheme { PersonalSuggestionsScreen(local.wishlist, local.catalogue, local.diary,
            F8BOfflineTaxonomy(), listOf(evidence), listOf(GeoPoint(45.0, 9.0)), onBack = {}) } }
        waitLoaded(); click("suggestion-details-${kingfisher.id}"); scroll("suggestion-reason-${kingfisher.id}")
        compose.onNodeWithTag("suggestion-reason-${kingfisher.id}").assertTextContains("ambienti acquatici", substring = true)
        compose.onNodeWithTag("suggestion-level-${kingfisher.id}").assertTextContains("INSUFFICIENT", substring = true)
        compose.onNodeWithText("Lipu, scheda Martin pescatore", substring = true).assertExists()
        capture("typical")
    }

    @Test fun savedTripSuggestionsReopenOfflineAndObsoleteSnapshotsDoNotClaimRelevance() {
        val trip = f8bTrip()
        val provider = F8BProvider()
        val explorer = f8bExplorer(provider)
        runBlocking {
            local.trips.save(trip)
            val result = explorer.explore(explorer.point("Milano", trip.destination.center),
                it.faunavia.plausibility.AnalysisPeriod(trip.startsOn, trip.endsOn)) as ExplorationResult.Ready
            local.trips.saveResult(resultSnapshot(result.taxa.single(), trip, null, result, f8bNow))
            local.wishlist.add(f8bTaxon.id)
            local.trips.save(trip.copy(endsOn = trip.endsOn.plusDays(1)))
        }
        provider.unavailable = true
        val fakeMap = object : ExplorationMapAdapter {
            @Composable override fun Render(analysis: RouteAnalysis) { Box(Modifier.height(60.dp).testTag("f9-map")) }
        }
        compose.setContent { FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified, local.catalogue,
            F8BOfflineTaxonomy(), PlaceSearch { PlaceSearchResult.Unavailable() }, explorer, local.routes, fakeMap,
            wishlist = local.wishlist) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("trip-select-t").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("trip-select-t").performClick()
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("trip-suggestions"))
        compose.onNodeWithTag("trip-suggestions").performClick()
        waitLoaded(); click("suggestion-view-WISHLIST"); click("wishlist-details-${f8bTaxon.id}"); scroll("wishlist-relevance-${f8bTaxon.id}")
        compose.onNodeWithTag("wishlist-relevance-${f8bTaxon.id}").assertTextContains("Pertinenza non valutabile", substring = true)
        runBlocking { local.trips.save(trip) }
        click("suggestion-back")
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("trip-suggestions"))
        compose.onNodeWithTag("trip-suggestions").performClick()
        waitLoaded(); click("suggestion-view-WISHLIST"); click("wishlist-details-${f8bTaxon.id}"); scroll("wishlist-relevance-${f8bTaxon.id}")
        compose.onNodeWithTag("wishlist-relevance-${f8bTaxon.id}").assertTextContains("DOCUMENTED", substring = true)
        assertEquals(1, provider.calls)
        assertEquals(1, runBlocking { local.trips.results(trip.id).size })
    }

    private fun show(view: SuggestionView = SuggestionView.TYPICAL) {
        compose.setContent { key(revision) { FaunaviaTheme { PersonalSuggestionsScreen(local.wishlist, local.catalogue,
            local.diary, F8BOfflineTaxonomy(), listOf(candidate), listOf(GeoPoint(45.0, 9.0)), initialView = view, onBack = {}) } } }
    }
    private fun reload() { compose.runOnUiThread { revision++ } }
    private fun waitLoaded() { compose.waitUntil(5_000) { compose.onAllNodesWithTag("suggestion-loading").fetchSemanticsNodes().isEmpty() } }
    private fun scroll(tag: String) { compose.onNodeWithTag("suggestion-list").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag: String) { scroll(tag); compose.onNodeWithTag(tag).performClick() }
    private fun capture(label: String) {
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (path?.let(::File) ?: File(context.filesDir, "golden-output")).apply { mkdirs() }
        File(directory, "f9-$label-actual.png").outputStream().use {
            compose.onNodeWithTag("suggestion-list").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
