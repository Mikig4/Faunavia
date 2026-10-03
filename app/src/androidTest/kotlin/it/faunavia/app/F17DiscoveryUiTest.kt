package it.faunavia.app

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.exploration.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class F17DiscoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<RouteTestActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val local get() = (context.applicationContext as FaunaviaApplication).repositories
    private val proposal = PilotOutingCatalogue.load().first { it.id == "brabbia-sentieri" }
    private val trip get() = f8bTrip().copy(destination = proposal.place, radiusMeters = 20_000.0)
    private val map = object : ExplorationMapAdapter { @Composable override fun Render(analysis: it.faunavia.route.RouteAnalysis) { androidx.compose.material3.Text("Mappa fixture") } }
    private fun wait(tag: String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(container: String, tag: String) {
        closeSoftKeyboard(); compose.onNodeWithTag(container).performScrollToNode(hasTestTag(tag)); compose.onNodeWithTag(tag).performClick()
    }
    @Composable private fun Screen(source: OutingCatalogue = PilotOutingCatalogue) {
        FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified, local.catalogue, F8BOfflineTaxonomy(),
            PlaceSearch { PlaceSearchResult.Unavailable() }, f8bExplorer(F8BProvider()), local.routes, map,
            now = { f8bNow }, idFactory = { "f17-memory" }, wishlist = local.wishlist, outingCatalogue = source) }
    }
    private fun selectTrip() { wait("trip-select-t"); click("trip-content", "trip-select-t"); wait("trip-detail") }

    @Test fun discoverySavesOnceRestoresSelectionAndCreatesALinkedDraftOffline() {
        runBlocking { local.trips.save(trip) }
        val tester = StateRestorationTester(compose)
        tester.setContent { Screen() }
        selectTrip(); click("trip-content", "outing-discover")
        wait("discovery-dialog")
        compose.onNodeWithTag("discovery-content").performScrollToNode(hasTestTag("discovery-brabbia-sentieri-no-trace"))
        compose.onNodeWithTag("discovery-brabbia-sentieri-no-trace").assertTextContains("Traccia non disponibile", substring = true)
        click("discovery-content", "discovery-save-brabbia-sentieri")
        val id = proposal.saveFor(trip, trip.startsOn, f8bNow).id
        compose.waitUntil(10_000) { runBlocking { local.trips.outing(id) != null } }
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("outing-guide-source-date"))
        compose.onNodeWithTag("outing-guide-source-date").assertTextContains(PilotOutingCatalogue.VERSION, substring = true)
        tester.emulateSavedInstanceStateRestore()
        wait("trip-detail")
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("outing-guide-no-trace"))
        compose.onNodeWithTag("outing-guide-no-trace").assertExists()
        click("trip-content", "outing-discover"); click("discovery-content", "discovery-save-brabbia-sentieri")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("discovery-dialog").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, runBlocking { local.trips.outings(trip.id).size })
        assertEquals(proposal.guide.copy(savedAt = f8bNow), runBlocking { local.trips.outing(id)!!.guide })
        click("trip-content", "trip-new-draft"); wait("diary-editor")
        compose.onNodeWithTag("diary-editor").performScrollToNode(hasTestTag("diary-notes"))
        compose.onNodeWithTag("diary-notes").performTextReplacement("Ricordo da identificare dopo la passeggiata")
        click("diary-editor", "diary-save")
        compose.waitUntil(10_000) { runBlocking { local.unidentified.get("f17-memory") != null } }
        val draft = runBlocking { local.unidentified.get("f17-memory")!! }
        assertEquals(id, draft.input.outingId); assertEquals(trip.id, draft.input.tripId)
        assertNull(draft.input.location); assertTrue(runBlocking { local.diary.list().isEmpty() })
    }
    @Test fun missingDurationAndInvalidDateFilterExplicitlyAndNoCoverageOffersManualFallback() {
        runBlocking { local.trips.save(trip) }
        compose.setContent { Screen() }; selectTrip(); click("trip-content", "outing-discover")
        click("discovery-content", "discovery-short")
        compose.onNodeWithTag("discovery-content").performScrollToNode(hasTestTag("discovery-empty"))
        compose.onNodeWithTag("discovery-empty").assertTextContains("manuale", substring = true)
        compose.onNodeWithTag("discovery-content").performScrollToNode(hasTestTag("discovery-date"))
        compose.onNodeWithTag("discovery-date").performTextReplacement("2027-01-01"); closeSoftKeyboard()
        compose.onNodeWithTag("discovery-date-error").assertExists()
        compose.onNodeWithTag("discovery-save-brabbia-sentieri").assertDoesNotExist()
        compose.onNodeWithTag("discovery-close").performClick()
        click("trip-content", "outing-new")
        wait("outing-editor")
        assertTrue(runBlocking { local.trips.outings(trip.id).isEmpty() })
    }
    @Test fun catalogueOutageRetainsSavedSourcesAndRetryRecoversWithoutReplacingSnapshot() {
        val saved = proposal.saveFor(trip, trip.startsOn, f8bNow).copy(guide = proposal.guide.copy(savedAt = f8bNow,
            source = proposal.guide.source.copy(retrievedAt = Instant.parse("2020-01-01T00:00:00Z"))))
        runBlocking { local.trips.save(trip); local.trips.saveOuting(saved) }
        var unavailable = true
        val source = OutingCatalogue { if (unavailable) error("offline source") else PilotOutingCatalogue.load() }
        compose.setContent { Screen(source) }; selectTrip()
        click("trip-content", "outing-select-${saved.id}")
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("outing-guide-stale"))
        compose.onNodeWithTag("outing-guide-stale").assertTextContains("Dati datati", substring = true)
        click("trip-content", "outing-discover")
        compose.onNodeWithTag("discovery-content").performScrollToNode(hasTestTag("discovery-warning"))
        compose.onNodeWithTag("discovery-warning").assertTextContains("salvate", substring = true)
        unavailable = false
        click("discovery-content", "discovery-retry"); click("discovery-content", "discovery-save-brabbia-sentieri")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("discovery-dialog").fetchSemanticsNodes().isEmpty() }
        assertEquals(saved, runBlocking { local.trips.outing(saved.id) })
    }
}
