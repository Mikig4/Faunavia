package it.faunavia.app

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.occurrence.*
import it.faunavia.plausibility.*
import it.faunavia.route.RouteAnalysis
import it.faunavia.testing.FakeClock
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F9PresentationTest {
    @get:Rule val compose = createAndroidComposeRule<RouteTestActivity>()
    private val local get() = (compose.activity.application as FaunaviaApplication).repositories
    private val map = object : ExplorationMapAdapter {
        @Composable override fun Render(analysis: RouteAnalysis) { Box(Modifier.height(60.dp).testTag("presentation-map")) }
    }

    private fun explorer(names: List<String>) = ExplorationService(OccurrenceGateway(object : OccurrenceCacheStore {
        override suspend fun read(key: String): OccurrenceCacheEntry? = null
        override suspend fun save(entry: OccurrenceCacheEntry) = Unit
        override suspend fun delete(key: String) = Unit
        override suspend fun clear() = Unit
    }, listOf(object : OccurrenceProvider {
        override val id = OccurrenceProviderId.GBIF
        override suspend fun search(scope: OccurrenceSearchScope) = OccurrenceProviderResult.Success(names.mapIndexed { index, name ->
            DocumentedOccurrence("gbif:record-$index", id, "record-$index", "gbif:p-$index", name,
                "2026-09-01", GeoPoint(45.0, 9.0), 20.0, f8bProvenance.source, f8bProvenance)
        })
    }), FakeClock(f8bNow.toEpochMilli())), now = { f8bNow })

    @Test fun researchStartsWithTypicalAnimalsAndCompactCardsWhileCompleteViewRestores() {
        val service = explorer(listOf("Ardea cinerea", "Turdus merula"))
        val tester = StateRestorationTester(compose)
        tester.setContent { FaunaviaTheme { ExplorationScreen(service, PlaceSearch { PlaceSearchResult.Unavailable() }, local.catalogue, map) } }
        analyze()
        scroll("explore-content", "explore-visible-count")
        compose.onNodeWithTag("explore-visible-count").assertTextContains("1 specie", substring = true)
        scroll("explore-content", "explore-taxon-gbif:p-0")
        compose.onNodeWithTag("species-name-gbif:p-0").assertTextContains("Airone cenerino")
        compose.onNodeWithTag("explore-level-gbif:p-0").assertDoesNotExist()
        compose.onNodeWithText("Immagine non disponibile", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Riconoscimento:", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("species-season-gbif:p-0").assertTextContains("segnale debole", substring = true)
        click("explore-content", "explore-details-gbif:p-0")
        scroll("explore-content", "explore-level-gbif:p-0")
        compose.onNodeWithTag("explore-level-gbif:p-0").assertTextContains("Documentato", substring = true)
        click("explore-content", "explore-view-all")
        scroll("explore-content", "explore-visible-count")
        compose.onNodeWithTag("explore-visible-count").assertTextContains("2 specie", substring = true)
        scroll("explore-content", "explore-taxon-gbif:p-1")
        compose.onNodeWithTag("species-name-gbif:p-1").assertTextContains("Merlo")
        tester.emulateSavedInstanceStateRestore()
        scroll("explore-content", "explore-results-title")
        scroll("explore-content", "explore-visible-count")
        compose.onNodeWithTag("explore-visible-count").assertTextContains("2 specie", substring = true)
    }

    @Test fun urbanOnlyResultsShowAnHonestEmptyTypicalSelectionAndRemainAccessible() {
        compose.setContent { FaunaviaTheme { ExplorationScreen(explorer(listOf("Turdus merula")),
            PlaceSearch { PlaceSearchResult.Unavailable() }, local.catalogue, map) } }
        analyze()
        scroll("explore-content", "explore-selection-empty")
        compose.onNodeWithTag("explore-selection-empty").assertTextContains("Non significa assenza", substring = true)
        click("explore-content", "explore-view-all")
        scroll("explore-content", "species-name-gbif:p-0")
        compose.onNodeWithTag("species-name-gbif:p-0").assertTextContains("Merlo")
    }

    @Test fun habitatOpensAnInternalObservationMapOnlyOnClickWithoutStartingABrowser() {
        var opened: Intent? = null
        var requests = 0
        val wrapper = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) {
                opened = intent
            }
        }
        val metadata = object : SpeciesMetadataLookup {
            override suspend fun name(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesNameMetadata>(null)
            override suspend fun distribution(id: String, scientificName: String, refresh: Boolean): MetadataResult<SpeciesDistribution> {
                requests++
                return MetadataResult(SpeciesDistribution(null, "123", f8bProvenance))
            }
        }
        val density = object : SpeciesDensityMapAdapter {
            @Composable override fun Render(gbifKey: String) { Box(Modifier.height(100.dp).testTag("internal-density-$gbifKey")) }
        }
        compose.setContent { CompositionLocalProvider(LocalContext provides wrapper, LocalSpeciesMetadata provides metadata,
            LocalSpeciesDensityMap provides density) { FaunaviaTheme {
            Column { EssentialSpeciesDetails("missing", "Alcedo atthis Linnaeus, 1758", local.catalogue, "Dato non disponibile") }
        } } }
        compose.waitForIdle(); assertNull(opened); assertEquals(0, requests)
        compose.onNodeWithTag("species-habitat-missing").performClick()
        wait("internal-density-123")
        compose.onNodeWithTag("species-distribution-observations").assertTextContains("osservazioni storiche", substring = true)
        compose.onNodeWithTag("species-distribution-close").performClick()
        compose.onNodeWithTag("species-distribution-dialog").assertDoesNotExist()
        compose.onNodeWithTag("species-name-missing").assertTextContains("Martin pescatore")
        assertEquals(1, requests); assertNull(opened)
    }

    @Test fun unknownSpeciesUsesScientificNameAndUnavailableDataWithoutInventedLinks() {
        compose.setContent { FaunaviaTheme { Column {
            EssentialSpeciesDetails("missing", "Unknown species", local.catalogue)
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("species-name-missing").assertTextContains("Unknown species")
        compose.onNodeWithTag("species-habitat-missing").assertTextContains("Dato documentato non disponibile", substring = true)
        compose.onNodeWithTag("species-habitat-missing").assertHasClickAction()
        compose.onNodeWithTag("species-season-missing").assertTextContains("Dato non disponibile", substring = true)
        compose.onNodeWithTag("species-habitat-missing").performClick()
        wait("species-distribution-unavailable")
        compose.onNodeWithTag("species-distribution-unavailable").assertTextContains("Non è prova di assenza", substring = true)
    }

    @Test fun speciesTitleHasVisibleInkEvenWhenTheParentPassesWhiteContentColor() {
        compose.setContent { FaunaviaTheme { CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.background(Color.White).padding(16.dp)) { EssentialSpeciesDetails("contrast", "Alcedo atthis", local.catalogue) }
        } } }
        val pixels = compose.onNodeWithTag("species-name-contrast").captureToImage().toPixelMap()
        var darkPixels = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val color = pixels[x, y]
            if (color.red < 0.6f && color.green < 0.6f && color.blue < 0.6f) darkPixels++
        }
        assertTrue("The animal title must be visible on the white card", darkPixels > 10)
    }

    @Test fun italianCommonNameOutsideThePilotIsEnrichedWithoutSelectingADiaryTaxon() {
        val metadata = object : SpeciesMetadataLookup {
            override suspend fun name(id: String, scientificName: String, refresh: Boolean) =
                MetadataResult(SpeciesNameMetadata("Scoiattolo comune", "123", f8bProvenance))
            override suspend fun distribution(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesDistribution>(null)
        }
        compose.setContent { FaunaviaTheme { CompositionLocalProvider(LocalSpeciesMetadata provides metadata) {
            Column { EssentialSpeciesDetails("remote-only", "Sciurus vulgaris", local.catalogue) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Scoiattolo comune").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("species-name-remote-only").assertTextContains("Scoiattolo comune")
        compose.onNodeWithText("Sciurus vulgaris").assertExists()
        assertNull(runBlocking { local.catalogue.taxon("remote-only") })
    }

    @Test fun illustratedRangeStaysInsideTheAppWithImageRetryAndObservationSwitch() {
        val metadata = object : SpeciesMetadataLookup {
            override suspend fun name(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesNameMetadata>(null)
            override suspend fun distribution(id: String, scientificName: String, refresh: Boolean) = MetadataResult(
                SpeciesDistribution(RangeIllustration("fixture-map", "2020", f8bProvenance), "123", f8bProvenance))
        }
        var imageCalls = 0
        val loader = object : SpeciesMapImageLoader {
            override fun load(url: String): Bitmap {
                if (++imageCalls == 1) throw java.io.IOException("offline")
                return Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            }
        }
        val density = object : SpeciesDensityMapAdapter {
            @Composable override fun Render(gbifKey: String) { Box(Modifier.height(100.dp).testTag("internal-density-$gbifKey")) }
        }
        compose.setContent { FaunaviaTheme { CompositionLocalProvider(LocalSpeciesMetadata provides metadata,
            LocalSpeciesImageLoader provides loader, LocalSpeciesDensityMap provides density) {
            SpeciesDistributionDialog("fixture", "Sciurus vulgaris") {}
        } } }
        wait("species-distribution-image-error")
        compose.onNodeWithText("Riprova immagine").performScrollTo().performClick()
        wait("species-distribution-image")
        compose.onNodeWithTag("species-distribution-image").assertExists()
        compose.onNodeWithText("Data indicata: 2020").assertExists()
        compose.onNodeWithText("Segnalazioni GBIF").performScrollTo().performClick()
        wait("internal-density-123")
        compose.onNodeWithTag("species-distribution-image").assertDoesNotExist()
        assertEquals(2, imageCalls)
    }

    @Test fun globalSpeciesDistributionUsesTheRealNativeMapRenderer() {
        compose.setContent { FaunaviaTheme { MapLibreSpeciesDensityMapAdapter.Render("2434507") } }
        wait("species-distribution-map")
        compose.onNodeWithTag("species-distribution-map").assertExists()
    }

    @Test fun tripResearchUsesTheSameDefaultAndKeepsPreviouslyChosenUrbanSnapshots() {
        val trip = f8bTrip("presentation-trip")
        val service = explorer(listOf("Ardea cinerea", "Turdus merula"))
        runBlocking {
            local.trips.save(trip)
            val result = service.explore(service.point("Milano", trip.destination.center), AnalysisPeriod(trip.startsOn, trip.endsOn)) as ExplorationResult.Ready
            local.trips.saveResult(resultSnapshot(result.taxa.first { it.scientificName == "Turdus merula" }, trip, null, result, f8bNow))
        }
        compose.setContent { FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified, local.catalogue,
            F8BOfflineTaxonomy(), PlaceSearch { PlaceSearchResult.Unavailable() }, service, local.routes, map) } }
        wait("trip-select-${trip.id}")
        compose.onNodeWithTag("trip-select-${trip.id}").performClick()
        click("trip-content", "trip-analyze")
        compose.waitForIdle()
        scroll("trip-content", "trip-live-results")
        compose.onNodeWithTag("trip-live-results").assertExists()
        scroll("trip-content", "explore-taxon-gbif:p-0")
        compose.onNodeWithTag("explore-taxon-gbif:p-0").assertExists()
        compose.onNodeWithTag("explore-taxon-gbif:p-1").assertDoesNotExist()
        click("trip-content", "trip-view-all")
        scroll("trip-content", "explore-taxon-gbif:p-1")
        compose.onNodeWithTag("explore-taxon-gbif:p-1").assertExists()
        assertEquals(1, runBlocking { local.trips.results(trip.id).size })
    }

    private fun analyze() {
        scroll("explore-content", "explore-latitude")
        compose.onNodeWithTag("explore-latitude").performTextInput("45.0")
        compose.onNodeWithTag("explore-longitude").performTextInput("9.0")
        closeSoftKeyboard(); click("explore-content", "explore-confirm-coordinates")
        scroll("explore-content", "explore-period-start")
        compose.onNodeWithTag("explore-period-start").performTextReplacement(LocalDate.of(2026, 10, 1).toString())
        compose.onNodeWithTag("explore-period-end").performTextReplacement(LocalDate.of(2026, 10, 7).toString())
        closeSoftKeyboard(); click("explore-content", "explore-analyze"); wait("explore-results-title")
    }
    private fun wait(tag: String) { compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun scroll(list: String, tag: String) { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)) }
    private fun click(list: String, tag: String) { scroll(list, tag); compose.onNodeWithTag(tag).performClick() }
}
