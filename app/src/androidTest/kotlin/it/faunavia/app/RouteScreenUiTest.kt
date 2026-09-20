package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.faunavia.domain.AppClock
import it.faunavia.domain.Route
import it.faunavia.domain.RouteRepository
import it.faunavia.route.RouteImportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteScreenUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<RouteTestActivity>()

    private val repository = MemoryRouteRepository()
    private val importer = RouteImportService(
        clock = object : AppClock { override fun nowEpochMillis(): Long = 1_758_196_800_000L },
        repository = repository,
    )

    @Test
    fun invalidCoordinatesShowAnInlineErrorWithoutSaving() {
        showRoutes()

        composeRule.onNodeWithTag("route-latitude").performTextInput("91")
        composeRule.onNodeWithTag("route-longitude").performTextInput("9.19")
        composeRule.onNodeWithTag("route-analyze-location").performClick()

        composeRule.onNodeWithTag("route-error").assertIsDisplayed().assertTextContains("WGS84 valide")
        assertEquals(0, runBlocking { repository.list().size })
    }

    @Test
    fun validLocationProducesAndPersistsTheDeterministicSummary() {
        showRoutes()

        composeRule.onNodeWithTag("route-latitude").performTextInput("45.52")
        composeRule.onNodeWithTag("route-longitude").performTextInput("9.18")
        composeRule.onNodeWithTag("route-analyze-location").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { runBlocking { repository.list().size == 1 } }
        composeRule.onNodeWithTag("route-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("route-projection").assertIsDisplayed().assertTextContains("EPSG:3035")
    }

    private fun showRoutes() {
        composeRule.setContent {
            FaunaviaTheme { RouteScreen(repository, importer) }
        }
    }

    private class MemoryRouteRepository : RouteRepository {
        private val routes = linkedMapOf<String, Route>()
        override suspend fun save(route: Route) { routes[route.id] = route }
        override suspend fun get(id: String): Route? = routes[id]
        override suspend fun list(): List<Route> = routes.values.toList()
        override suspend fun delete(id: String) { routes.remove(id) }
    }
}
