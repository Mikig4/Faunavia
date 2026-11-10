package it.faunavia.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.faunavia.exploration.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutingSearchLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<AnalysisTestActivity>()
    @After fun clearHost() { AnalysisTestActivity.testContent = null }

    @Test fun inFlightAndCompletedOutingSearchSurviveRealActivityRecreationWithoutAnotherCall() {
        val local = (compose.activity.application as FaunaviaApplication).repositories
        runBlocking { local.trips.save(f8bTrip()) }
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val source = OutingSearch { calls.incrementAndGet(); gate.await(); OutingSearchResult(emptyList()) }
        compose.activityRule.scenario.onActivity { it.showTestContent {
            FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified, local.catalogue, F8BOfflineTaxonomy(),
                PlaceSearch { PlaceSearchResult.Unavailable() }, f8bExplorer(F8BProvider()), local.routes, outingSearch = source) }
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("trip-select-t").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("trip-select-t"))
        compose.onNodeWithTag("trip-select-t").performClick()
        compose.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("outing-discover"))
        compose.onNodeWithTag("outing-discover").performClick()
        compose.waitUntil(10_000) { calls.get() == 1 }
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        assertNotSame(before, compose.activity)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("discovery-loading").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, calls.get())
        fun awaitEmptyResults() {
            // Recreation first reloads the selected trip from Room; a missing loader is not readiness.
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onNodeWithTag("discovery-content").performScrollToNode(hasTestTag("discovery-empty"))
                    compose.onNodeWithTag("discovery-empty").assertIsDisplayed()
                }.isSuccess
            }
        }
        gate.complete(Unit)
        awaitEmptyResults()
        compose.activityRule.scenario.recreate()
        awaitEmptyResults()
        assertEquals(1, calls.get())
    }
}
