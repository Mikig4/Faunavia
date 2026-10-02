package it.faunavia.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.occurrence.*
import it.faunavia.route.RouteAnalysis
import it.faunavia.testing.FakeClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<AnalysisTestActivity>()
    private val local get() = (compose.activity.application as FaunaviaApplication).repositories
    private val map = object : ExplorationMapAdapter {
        @Composable override fun Render(analysis: RouteAnalysis) { Box(Modifier.height(60.dp).testTag("lifecycle-map")) }
    }
    @After fun clearHost() { AnalysisTestActivity.testContent = null }

    private fun explorer(calls: AtomicInteger, gate: CompletableDeferred<Unit>? = null) = ExplorationService(OccurrenceGateway(object : OccurrenceCacheStore {
        override suspend fun read(key: String): OccurrenceCacheEntry? = null
        override suspend fun save(entry: OccurrenceCacheEntry) = Unit
        override suspend fun delete(key: String) = Unit
        override suspend fun clear() = Unit
    }, listOf(object : OccurrenceProvider {
        override val id = OccurrenceProviderId.GBIF
        override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult {
            calls.incrementAndGet(); gate?.await()
            return OccurrenceProviderResult.Success(listOf(DocumentedOccurrence("gbif:lifecycle-record", id, "lifecycle-record", "gbif:lifecycle",
                "Ardea cinerea", "2026-09-01", GeoPoint(45.0, 9.0), 20.0, f8bProvenance.source, f8bProvenance)))
        }
    }), FakeClock(f8bNow.toEpochMilli())), now = { f8bNow })

    private fun content(render: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { it.showTestContent(render) }
    }

    @Test fun completedAnalysisSurvivesActivityRecreationWithoutAnotherProviderCall() {
        val calls = AtomicInteger()
        val service = explorer(calls)
        val catalogue = local.catalogue
        content { FaunaviaTheme { ExplorationScreen(service, PlaceSearch { PlaceSearchResult.Unavailable() }, catalogue, map) } }
        analyze(); wait("explore-results-title")
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        assertNotSame(before, compose.activity)
        scroll("explore-content", "explore-latitude")
        compose.onNodeWithTag("explore-latitude").assertTextContains("45.0", substring = true)
        compose.onNodeWithTag("explore-longitude").assertTextContains("9.0", substring = true)
        scroll("explore-content", "species-name-gbif:lifecycle")
        compose.onNodeWithTag("species-name-gbif:lifecycle").assertTextContains("Airone cenerino")
        assertEquals(1, calls.get())
    }

    @Test fun inFlightAnalysisContinuesThroughActivityRecreationAndDeliversItsResultOnce() {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val service = explorer(calls, gate)
        val catalogue = local.catalogue
        content { FaunaviaTheme { ExplorationScreen(service, PlaceSearch { PlaceSearchResult.Unavailable() }, catalogue, map) } }
        analyze()
        compose.waitUntil(5_000) { calls.get() == 1 }
        compose.activityRule.scenario.recreate()
        scroll("explore-content", "explore-loading")
        compose.onNodeWithTag("explore-loading").assertExists()
        gate.complete(Unit)
        wait("explore-results-title")
        scroll("explore-content", "species-name-gbif:lifecycle")
        compose.onNodeWithTag("species-name-gbif:lifecycle").assertTextContains("Airone cenerino")
        assertEquals(1, calls.get())
    }

    @Test fun tripLiveResultsSurviveActivityRecreationWithoutSavingOrRequerying() {
        val calls = AtomicInteger()
        val service = explorer(calls)
        val repositories = local
        val trip = f8bTrip("lifecycle-trip")
        runBlocking { repositories.trips.save(trip) }
        content { FaunaviaTheme { TripsScreen(repositories.trips, repositories.diary, repositories.unidentified,
            repositories.catalogue, F8BOfflineTaxonomy(), PlaceSearch { PlaceSearchResult.Unavailable() }, service, repositories.routes, map) } }
        wait("trip-select-${trip.id}")
        compose.onNodeWithTag("trip-select-${trip.id}").performClick()
        click("trip-content", "trip-analyze")
        scroll("trip-content", "trip-live-results")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("trip-loading").fetchSemanticsNodes().isEmpty() }
        scroll("trip-content", "trip-detail")
        scroll("trip-content", "species-name-gbif:lifecycle")
        compose.onNodeWithTag("species-name-gbif:lifecycle").assertTextContains("Airone cenerino")
        assertEquals(1, calls.get())
        assertTrue(runBlocking { repositories.trips.results(trip.id).isEmpty() })
    }

    private fun analyze() {
        scroll("explore-content", "explore-latitude")
        compose.onNodeWithTag("explore-latitude").performTextInput("45.0")
        compose.onNodeWithTag("explore-longitude").performTextInput("9.0")
        closeSoftKeyboard(); click("explore-content", "explore-confirm-coordinates")
        click("explore-content", "explore-analyze")
    }
    private fun wait(tag: String) { compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun scroll(list: String, tag: String) { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)) }
    private fun click(list: String, tag: String) { scroll(list, tag); compose.onNodeWithTag(tag).performClick() }
}
