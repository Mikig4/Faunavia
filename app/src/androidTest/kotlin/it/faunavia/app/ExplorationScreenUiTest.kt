package it.faunavia.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Provenance
import it.faunavia.domain.Route
import it.faunavia.domain.RouteRepository
import it.faunavia.domain.RouteSource
import it.faunavia.exploration.ExplorationService
import it.faunavia.exploration.PlaceCandidate
import it.faunavia.exploration.PlaceKind
import it.faunavia.exploration.PlaceOrigin
import it.faunavia.exploration.PlaceSearch
import it.faunavia.exploration.PlaceSearchResult
import it.faunavia.occurrence.DocumentedOccurrence
import it.faunavia.occurrence.OccurrenceCacheEntry
import it.faunavia.occurrence.OccurrenceCacheStore
import it.faunavia.occurrence.OccurrenceGateway
import it.faunavia.occurrence.OccurrenceProvider
import it.faunavia.occurrence.OccurrenceProviderId
import it.faunavia.occurrence.OccurrenceProviderResult
import it.faunavia.occurrence.OccurrenceSearchScope
import it.faunavia.route.RouteAnalysis
import java.io.File
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExplorationScreenUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<RouteTestActivity>()

    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val catalogue get() = (composeRule.activity.application as FaunaviaApplication).repositories.catalogue
    private val fakeMap = object : ExplorationMapAdapter {
        @Composable override fun Render(analysis: RouteAnalysis) {
            Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFDFEDE2)).testTag("fake-map"))
        }
    }

    @Test fun ambiguousPlaceRequiresExplicitConfirmationAndShowsSampleArea() {
        val cities = listOf(
            PlaceCandidate("1", "Milano, Italia", PlaceKind.CITY, "it", GeoPoint(45.46, 9.19), null),
            PlaceCandidate("2", "Milano, altro", PlaceKind.REGION, "it", GeoPoint(45.5, 9.2), null),
        )
        val geocoder = PlaceSearch { PlaceSearchResult.Matches(cities, PlaceOrigin.NETWORK) }
        show(geocoder)
        composeRule.onNodeWithTag("place-query").performTextInput("Milano")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("place-search").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("place-confirm-1").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("place-confirm-1").performClick()
        scrollTo("explore-sample-disclaimer")
        composeRule.onNodeWithTag("explore-sample-disclaimer")
            .assertTextContains("non copre l’intero territorio", substring = true)
        composeRule.onNodeWithTag("place-attribution").assertExists()
    }

    @Test fun emptyAndUnavailableGeocoderStatesKeepCoordinateEntry() {
        val geocoder = PlaceSearch { query ->
            if (query == "vuoto") PlaceSearchResult.Matches(emptyList(), PlaceOrigin.NETWORK)
            else PlaceSearchResult.Unavailable()
        }
        show(geocoder)
        composeRule.onNodeWithTag("place-query").performTextInput("vuoto")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("place-search").performClick()
        composeRule.waitUntil(5_000) { composeRule.onNodeWithTag("place-message").fetchSemanticsNode().config.toString().contains("Nessun luogo") }
        composeRule.onNodeWithTag("place-message").assertTextContains("Nessun luogo", substring = true)
        composeRule.onNodeWithTag("place-query").performTextReplacement("offline")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("place-search").performClick()
        composeRule.waitUntil(5_000) { composeRule.onNodeWithTag("place-message").fetchSemanticsNode().config.toString().contains("non disponibile") }
        composeRule.onNodeWithTag("place-message").assertTextContains("non disponibile", substring = true)
        scrollTo("explore-confirm-coordinates")
        composeRule.onNodeWithTag("explore-confirm-coordinates").assertIsDisplayed()
    }

    @Test fun coordinatesFiltersMapAttributionAndRestorationWorkWithoutNetwork() {
        val tester = StateRestorationTester(composeRule)
        val provider = FixtureProvider(listOf(record("1", "Turdus merula", "2026-04-12"),
            record("2", "Corvus cornix", "2010-04-12")))
        val explorer = explorer(provider)
        tester.setContent { FaunaviaTheme { ExplorationScreen(explorer, PlaceSearch { PlaceSearchResult.Unavailable() }, catalogue, fakeMap) } }
        confirmCoordinates()
        analyze()
        scrollTo("explore-results-title")
        composeRule.onNodeWithTag("explore-results-title").assertIsDisplayed()
        scrollTo("explore-toggle-map")
        composeRule.onNodeWithTag("explore-toggle-map").performClick()
        scrollTo("map-attribution")
        composeRule.onNodeWithTag("map-attribution").assertTextContains("OpenStreetMap", substring = true)
        scrollTo("explore-filters")
        composeRule.onNodeWithTag("filter-documented").performClick()
        scrollTo("explore-taxon-gbif:1")
        scrollTo("explore-details-gbif:1")
        composeRule.onNodeWithTag("explore-details-gbif:1").performClick()
        composeRule.onNodeWithTag("explore-level-gbif:1").assertTextContains("Documentato", substring = true)
        scrollTo("explore-license-1")
        composeRule.onNodeWithTag("explore-license-1").assertTextContains("CC BY", substring = true)
        composeRule.onNodeWithTag("explore-taxon-gbif:2").assertDoesNotExist()
        tester.emulateSavedInstanceStateRestore()
        scrollTo("explore-confirmed")
        composeRule.onNodeWithTag("explore-confirmed").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("explore-results-title").fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("explore-results-title")
        composeRule.onNodeWithTag("explore-results-title").assertIsDisplayed()
        scrollTo("map-attribution")
        composeRule.onNodeWithTag("map-attribution").assertTextContains("OpenStreetMap", substring = true)
        scrollTo("explore-taxon-gbif:1")
        composeRule.onNodeWithTag("explore-level-gbif:1").assertTextContains("Documentato", substring = true)
        composeRule.onNodeWithTag("explore-taxon-gbif:2").assertDoesNotExist()
    }

    @Test fun mapListAndErrorScreenshotsMatchVersionedSignature() {
        val baseline = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("golden/exploration-v1.json")
            .bufferedReader().use { it.readText() })
        val provider = FixtureProvider(listOf(record("1", "Turdus merula", "2026-04-12")))
        show(PlaceSearch { PlaceSearchResult.Unavailable() }, explorer(provider))
        confirmCoordinates()
        analyze()
        scrollTo("explore-taxon-gbif:1")
        val listImage = composeRule.onNodeWithTag("explore-taxon-gbif:1").captureToImage()
        assertEquals(baseline.getString("listCard"), argb(listImage.toPixelMap()[listImage.width / 2, 1].toArgb()))
        saveScreenshot("list", "explore-taxon-gbif:1")
        scrollTo("explore-toggle-map")
        composeRule.onNodeWithTag("explore-toggle-map").performClick()
        scrollTo("fake-map")
        val mapImage = composeRule.onNodeWithTag("fake-map").captureToImage()
        val mapPixel = argb(mapImage.toPixelMap()[mapImage.width / 2, mapImage.height / 2].toArgb())
        assertEquals(baseline.getString("fakeMap"), mapPixel)
        saveScreenshot("map", "fake-map")
        scrollTo("explore-latitude")
        composeRule.onNodeWithTag("explore-latitude").performTextReplacement("91")
        closeSoftKeyboard()
        scrollTo("explore-confirm-coordinates")
        composeRule.onNodeWithTag("explore-confirm-coordinates").performClick()
        scrollTo("explore-error")
        composeRule.onNodeWithTag("explore-error").assertTextContains("WGS84", substring = true)
        val errorImage = composeRule.onNodeWithTag("screen-results").captureToImage()
        assertEquals(baseline.getString("header"), argb(errorImage.toPixelMap()[2, 2].toArgb()))
        saveScreenshot("error", "screen-results")
        assertEquals(3, baseline.getJSONArray("states").length())
    }

    @Test fun mapLibreAdapterKeepsTextResultsAvailableWhenTilesAreMissing() {
        val provider = FixtureProvider(listOf(record("1", "Turdus merula", "2026-04-12")))
        composeRule.setContent {
            FaunaviaTheme { ExplorationScreen(explorer(provider), PlaceSearch { PlaceSearchResult.Unavailable() },
                catalogue, MapLibreExplorationMapAdapter) }
        }
        confirmCoordinates()
        analyze()
        scrollTo("explore-toggle-map")
        composeRule.onNodeWithTag("explore-toggle-map").performClick()
        scrollTo("explore-map")
        composeRule.onNodeWithTag("explore-map").assertIsDisplayed()
        scrollTo("map-attribution")
        composeRule.onNodeWithTag("map-attribution").assertIsDisplayed()
        scrollTo("explore-taxon-gbif:1")
        scrollTo("explore-details-gbif:1")
        composeRule.onNodeWithTag("explore-details-gbif:1").performClick()
        composeRule.onNodeWithTag("explore-level-gbif:1").assertTextContains("Documentato", substring = true)
    }

    @Test fun previouslySavedRouteCanBeExploredWithoutReimporting() {
        val route = Route("saved-1", "Parco Nord", listOf(GeoPoint(45.0, 9.0)), now, RouteSource.GPX)
        val repository = object : RouteRepository {
            override suspend fun save(route: Route) = Unit
            override suspend fun get(id: String): Route? = route.takeIf { it.id == id }
            override suspend fun list(): List<Route> = listOf(route)
            override suspend fun delete(id: String) = Unit
        }
        val provider = FixtureProvider(listOf(record("1", "Turdus merula", "2026-04-12")))
        composeRule.setContent {
            FaunaviaTheme { ExplorationScreen(explorer(provider), PlaceSearch { PlaceSearchResult.Unavailable() },
                catalogue, fakeMap, repository) }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("explore-saved-0").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("explore-saved-0")
        composeRule.onNodeWithTag("explore-saved-0").performClick()
        scrollTo("explore-confirmed")
        composeRule.onNodeWithTag("explore-selected-name").assertTextContains("Parco Nord", substring = true)
        analyze()
        scrollTo("explore-results-title")
        composeRule.onNodeWithTag("explore-results-title").assertTextContains("Parco Nord", substring = true)
    }

    @Test fun confirmedPlaceCacheSurvivesAdapterRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val place = PlaceCandidate("102", "Lombardia, Italia", PlaceKind.REGION, "it", GeoPoint(45.46, 9.19), null)
        PlaceCachePreferences(context).write("Lombardia", listOf(place))
        assertEquals(listOf(place), PlaceCachePreferences(context).read("LOMBARDIA"))
    }

    private fun show(geocoder: PlaceSearch, explorer: ExplorationService = explorer(FixtureProvider(emptyList()))) {
        composeRule.setContent { FaunaviaTheme { ExplorationScreen(explorer, geocoder, catalogue, fakeMap) } }
    }

    private fun confirmCoordinates() {
        scrollTo("explore-latitude")
        composeRule.onNodeWithTag("explore-latitude").performTextInput("45.0")
        composeRule.onNodeWithTag("explore-longitude").performTextInput("9.0")
        closeSoftKeyboard()
        scrollTo("explore-confirm-coordinates")
        composeRule.onNodeWithTag("explore-confirm-coordinates").performClick()
        scrollTo("explore-confirmed")
    }

    private fun analyze() {
        scrollTo("explore-analyze")
        composeRule.onNodeWithTag("explore-analyze").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("explore-results-title").fetchSemanticsNodes().isNotEmpty()
        }
        // Existing F8A sentinels exercise the complete evidence view, including urban taxa.
        scrollTo("explore-view-all")
        composeRule.onNodeWithTag("explore-view-all").performClick()
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("explore-content").performScrollToNode(hasTestTag(tag))
    }

    private fun saveScreenshot(name: String, tag: String) {
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "golden-output").apply { mkdirs() }
        File(directory, "exploration-$name-actual.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(image.width > 0 && image.height > 0)
    }

    private fun argb(value: Int): String = value.toUInt().toString(16).padStart(8, '0').uppercase()

    private fun explorer(provider: FixtureProvider): ExplorationService = ExplorationService(
        OccurrenceGateway(MemoryCache(), listOf(provider), object : AppClock {
            override fun nowEpochMillis(): Long = now.toEpochMilli()
        }), now = { now },
    )

    private fun record(id: String, name: String, date: String): DocumentedOccurrence = DocumentedOccurrence(
        id = "gbif:$id", provider = OccurrenceProviderId.GBIF, providerRecordId = id, taxonId = "gbif:$id",
        scientificName = name, observedOn = date, location = GeoPoint(45.0, 9.0),
        coordinateUncertaintyMeters = 100.0, sourceUrl = "https://example.test/$id",
        provenance = Provenance("GBIF", id, "fixture", now, "CC BY", "Fixture", "fixture", "v1"),
    )

    private class FixtureProvider(private val records: List<DocumentedOccurrence>) : OccurrenceProvider {
        override val id = OccurrenceProviderId.GBIF
        @Volatile var calls = 0
        override suspend fun search(scope: OccurrenceSearchScope): OccurrenceProviderResult {
            calls++
            return OccurrenceProviderResult.Success(records)
        }
    }

    private class MemoryCache : OccurrenceCacheStore {
        private val entries = mutableMapOf<String, OccurrenceCacheEntry>()
        override suspend fun read(key: String): OccurrenceCacheEntry? = entries[key]
        override suspend fun save(entry: OccurrenceCacheEntry) { entries[entry.key] = entry }
        override suspend fun delete(key: String) { entries.remove(key) }
        override suspend fun clear() { entries.clear() }
    }
}
