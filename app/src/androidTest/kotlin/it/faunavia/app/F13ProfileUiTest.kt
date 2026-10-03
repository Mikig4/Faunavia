package it.faunavia.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.exploration.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import androidx.test.espresso.Espresso.closeSoftKeyboard

@RunWith(AndroidJUnit4::class)
class F13ProfileUiTest {
    @get:Rule val compose = createAndroidComposeRule<RouteTestActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val local get() = (context.applicationContext as FaunaviaApplication).repositories
    private fun wait(tag: String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun profileClick(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun explorerClick(tag: String) { compose.onNodeWithTag("explore-content").performScrollToNode(hasTestTag(tag)); compose.onNodeWithTag(tag).performClick() }
    private fun screenshot(name: String) {
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(path ?: context.filesDir.resolve("golden-output").path).apply { mkdirs() }
        File(directory, name).outputStream().use {
            compose.onNodeWithTag("species-profile-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun sourcedCuriosityOpensOnlyOnRequestSurvivesRecreationAndHandlesExternalLinkFailure() {
        var calls = 0
        var opened: String? = null
        val uriHandler = object : UriHandler { override fun openUri(uri: String) { if (++calls == 1) throw IllegalStateException("No browser"); opened = uri } }
        val tester = StateRestorationTester(compose)
        tester.setContent { CompositionLocalProvider(LocalProfileSourceOpener provides uriHandler::openUri) { FaunaviaTheme {
            Column { SpeciesProfileAction("f13-view", "Turdus merula", local.catalogue) }
        } } }
        compose.onNodeWithTag("species-profile-f13-view").performClick()
        wait("profile-version")
        compose.onNodeWithTag("profile-title").assertTextContains("Merlo")
        compose.onNodeWithTag("profile-curiosity-0").assertDoesNotExist()
        profileClick("profile-curiosities-toggle")
        compose.onNodeWithTag("profile-curiosity-0").assertTextContains("maschio", substring = true)
        compose.onNodeWithTag("profile-curiosity-0").performScrollTo()
        screenshot("f13-curiosity.png")
        tester.emulateSavedInstanceStateRestore()
        wait("profile-version")
        compose.onNodeWithTag("profile-curiosity-0").assertExists()
        profileClick("profile-source-curiosity-0")
        compose.onNodeWithTag("profile-source-details-curiosity-0").assertTextContains("CC BY-NC-ND 4.0", substring = true)
        profileClick("profile-source-link-curiosity-0")
        compose.onNodeWithTag("profile-source-error-curiosity-0").assertExists()
        profileClick("profile-source-link-curiosity-0")
        assertEquals("https://www.lipu.it/uccelli/conoscerli-proteggerli/merlo", opened)
        compose.onNodeWithTag("profile-close").performClick()
        compose.onNodeWithTag("species-profile-dialog").assertDoesNotExist()
        assertNull(runBlocking { local.catalogue.taxon("f13-view") })
    }

    @Test fun uncoveredSpeciesAndUnreadableFallbackStayAccessibleAtLargeFontWithoutInventedFacts() {
        val loader = ProfileFallbackLoader { throw java.io.IOException("Missing or corrupt drawable") }
        compose.setContent { CompositionLocalProvider(LocalProfileFallback provides loader,
            LocalDensity provides Density(context.resources.displayMetrics.density, 1.8f)) { FaunaviaTheme {
            SpeciesProfileDialog("unknown", "Uncovered animal") {}
        } } }
        wait("profile-version")
        compose.onNodeWithTag("profile-title").assertTextContains("Uncovered animal")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("profile-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1.8f, layouts.single().layoutInput.density.fontScale, .01f)
        screenshot("f13-large-text.png")
        assertTrue(compose.onNodeWithTag("profile-title").fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        compose.onNodeWithTag("profile-fallback").assertContentDescriptionContains("Simbolo generico", substring = true)
        compose.onNodeWithTag("profile-fallback-recovery").assertExists()
        compose.onNodeWithTag("profile-name-source-unavailable").assertExists()
        profileClick("profile-curiosities-toggle")
        compose.onNodeWithTag("profile-curiosities-unavailable").assertExists()
        compose.onNodeWithTag("profile-field-DIET").performScrollTo().assertTextContains("Dato documentato non disponibile")
        compose.onNodeWithTag("profile-field-CONSERVATION").performScrollTo().assertTextContains("Dato documentato non disponibile")
        compose.onNodeWithTag("profile-close").assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithText("Apri fonte originale").assertCountEquals(0)
    }

    @Test fun partialLongProfileHasVisibleRetryAndRejectsAnUnsafeSourceLink() {
        var calls = 0
        val profile = ReadableSpeciesProfile("Testus longus", null, null, mapOf(ProfileField.DIET to ProfileFact(
            "Note verificate con accenti à è ì ò ù. ".repeat(200), f8bProvenance.copy(source = "javascript:alert(1)"))), contentVersion = "synthetic-long-v1")
        val lookup = SpeciesProfileLookup { _, _, _ -> if (++calls == 1) throw java.io.IOException("read failed") else SpeciesProfileResult(profile) }
        compose.setContent { FaunaviaTheme { SpeciesProfileDialog("partial", "Testus longus", lookup = lookup) {} } }
        wait("profile-warning"); profileClick("profile-retry"); wait("profile-field-DIET")
        compose.onNodeWithTag("profile-warning").assertDoesNotExist()
        compose.onNodeWithTag("profile-field-DIET").performScrollTo().assertTextContains("à è ì ò ù", substring = true)
        profileClick("profile-source-DIET")
        compose.onNodeWithTag("profile-source-unavailable-DIET").assertExists()
        compose.onNodeWithTag("profile-source-link-DIET").assertDoesNotExist()
        compose.onNodeWithTag("profile-field-SIZE").performScrollTo().assertTextContains("Dato documentato non disponibile")
        assertEquals(2, calls)
    }

    @Test fun catalogueProfileViewingDoesNotSelectOrSaveATaxon() {
        compose.setContent { FaunaviaTheme { CatalogueScreen(F8BOfflineTaxonomy(), 0, catalogue = local.catalogue) } }
        compose.onNodeWithTag("catalogue-query").performTextInput("merlo")
        closeSoftKeyboard()
        wait("species-profile-${f8bTaxon.id}")
        compose.onNodeWithTag("species-profile-${f8bTaxon.id}").performClick(); wait("profile-version")
        compose.onNodeWithTag("profile-title").assertTextContains("Merlo")
        profileClick("profile-source-taxonomy")
        compose.onNodeWithTag("profile-source-details-taxonomy").assertTextContains("Faunavia tests", substring = true)
        compose.onNodeWithTag("profile-close").performClick()
        compose.onNodeWithTag("catalogue-selected").assertDoesNotExist()
    }

    @Test fun researchProfileDistributionAndReturnKeepTheSameAnalysisAndEvidence() {
        val provider = F8BProvider()
        val service = f8bExplorer(provider)
        val metadata = object : SpeciesMetadataLookup {
            override suspend fun name(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesNameMetadata>(null)
            override suspend fun distribution(id: String, scientificName: String, refresh: Boolean) = MetadataResult(SpeciesDistribution(null, "123", f8bProvenance))
        }
        val density = object : SpeciesDensityMapAdapter { @Composable override fun Render(gbifKey: String) { Box(Modifier.height(80.dp).testTag("f13-density")) } }
        val map = object : ExplorationMapAdapter { @Composable override fun Render(analysis: it.faunavia.route.RouteAnalysis) { Box(Modifier.height(80.dp).testTag("f13-map")) } }
        compose.setContent { CompositionLocalProvider(LocalSpeciesMetadata provides metadata, LocalSpeciesDensityMap provides density) { FaunaviaTheme {
            ExplorationScreen(service, PlaceSearch { PlaceSearchResult.Unavailable() }, local.catalogue, map)
        } } }
        compose.onNodeWithTag("explore-content").performScrollToNode(hasTestTag("explore-latitude"))
        compose.onNodeWithTag("explore-latitude").performTextInput("45.0")
        compose.onNodeWithTag("explore-longitude").performTextInput("9.0")
        closeSoftKeyboard(); explorerClick("explore-confirm-coordinates")
        compose.onNodeWithTag("explore-content").performScrollToNode(hasTestTag("explore-period-start"))
        compose.onNodeWithTag("explore-period-start").performTextReplacement(LocalDate.of(2026, 10, 1).toString())
        compose.onNodeWithTag("explore-period-end").performTextReplacement(LocalDate.of(2026, 10, 7).toString())
        closeSoftKeyboard(); explorerClick("explore-analyze"); wait("explore-results-title")
        explorerClick("explore-view-all")
        explorerClick("species-profile-${f8bTaxon.id}"); wait("profile-version")
        profileClick("profile-distribution"); wait("f13-density")
        compose.onNodeWithTag("species-distribution-close").performClick()
        compose.onNodeWithTag("species-profile-dialog").assertExists()
        compose.onNodeWithTag("profile-close").performClick()
        explorerClick("explore-details-${f8bTaxon.id}")
        compose.onNodeWithTag("explore-content").performScrollToNode(hasTestTag("explore-level-${f8bTaxon.id}"))
        compose.onNodeWithTag("explore-level-${f8bTaxon.id}").assertTextContains("Documentato", substring = true)
        explorerClick("explore-toggle-map"); compose.onNodeWithTag("f13-map").assertExists()
        assertEquals(1, provider.calls)
    }

    @Test fun speciesProfileMatchesVersionedSignatureAndReadableTitleContrast() {
        compose.setContent { FaunaviaTheme { SpeciesProfileDialog("golden", "Alcedo atthis") {} } }
        wait("profile-version")
        val baseline = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("golden/profile-v1.json").bufferedReader().use { it.readText() })
        val image = compose.onNodeWithTag("species-profile-dialog").captureToImage()
        val pixels = image.toPixelMap()
        listOf(.01f, .99f).forEach { x ->
            assertEquals(baseline.getString("surfaceArgb"), argb(pixels[((pixels.width - 1) * x).toInt(), pixels.height / 2].toArgb()))
        }
        val title = compose.onNodeWithTag("profile-title").captureToImage().toPixelMap()
        val background = title[0, 0].luminance()
        var contrasting = 0
        for (x in 0 until title.width) for (y in 0 until title.height) {
            val foreground = title[x, y].luminance()
            if ((maxOf(background, foreground) + .05) / (minOf(background, foreground) + .05) >= baseline.getDouble("minimumTitleContrast")) contrasting++
        }
        assertTrue("Title contains readable text", contrasting > 80)
        val fallback = compose.onNodeWithTag("profile-fallback").captureToImage().toPixelMap()
        var ink = 0
        for (x in 0 until fallback.width) for (y in 0 until fallback.height) if (argb(fallback[x, y].toArgb()) == baseline.getString("silhouetteArgb")) ink++
        assertTrue("2D fallback is visible", ink > 100)
        screenshot("f13-profile.png")
    }
    private fun argb(value: Int) = value.toUInt().toString(16).padStart(8, '0').uppercase()
}
