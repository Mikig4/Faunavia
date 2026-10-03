package it.faunavia.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.exploration.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class WikipediaPhotoUiTest {
    @get:Rule val compose = createAndroidComposeRule<RouteTestActivity>()
    private val photo = SpeciesPhoto("https://thumb.wikimedia.org/synthetic-bird.png", "https://it.wikipedia.org/wiki/Turdus_merula", "2020",
        f8bProvenance.copy(source = "https://commons.wikimedia.org/wiki/File:Synthetic.png", attribution = "Synthetic test photographer", license = "CC0"))
    private val lookup = object : SpeciesMetadataLookup {
        override suspend fun name(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesNameMetadata>(null)
        override suspend fun distribution(id: String, scientificName: String, refresh: Boolean) = MetadataResult<SpeciesDistribution>(null)
        override suspend fun photo(id: String, scientificName: String, refresh: Boolean) = MetadataResult(photo, "Cache offline", true)
    }
    private fun wait(tag: String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }

    @Test fun photoCreditsWikipediaLinkAndStaleSourceSurviveRestorationWithoutSelectingTaxonomy() {
        var opened = ""
        val loader = object : SpeciesMapImageLoader { override fun load(url: String): Bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) } }
        val tester = StateRestorationTester(compose)
        tester.setContent { CompositionLocalProvider(LocalSpeciesMetadata provides lookup, LocalSpeciesImageLoader provides loader,
            LocalProfileSourceOpener provides { opened = it }) { FaunaviaTheme {
            SpeciesProfileAction("photo-test", "Turdus merula")
        } } }
        compose.onNodeWithTag("species-profile-photo-test").performClick(); wait("profile-wikipedia-image")
        compose.onNodeWithTag("profile-wikipedia-image").assertContentDescriptionContains("Turdus merula", substring = true)
        compose.onNodeWithTag("profile-photo-credits").assertTextContains("Synthetic test photographer", substring = true)
        compose.onNodeWithTag("profile-photo-stale").assertExists()
        compose.onNodeWithTag("profile-wikipedia-link").performScrollTo().performClick()
        assertEquals(photo.articleUrl, opened)
        tester.emulateSavedInstanceStateRestore(); wait("profile-wikipedia-image")
        compose.onNodeWithTag("profile-photo-credits").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repositories = (context.applicationContext as FaunaviaApplication).repositories
        assertNull(kotlinx.coroutines.runBlocking { repositories.catalogue.taxon("photo-test") })
    }
    @Test fun imageFailureShowsFallbackAndRetryRecoversWithTheSameVerifiedPhoto() {
        var fail = true
        val loader = object : SpeciesMapImageLoader { override fun load(url: String): Bitmap {
            if (fail) throw IOException("offline image")
            return Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        } }
        compose.setContent { CompositionLocalProvider(LocalSpeciesMetadata provides lookup, LocalSpeciesImageLoader provides loader) {
            FaunaviaTheme { SpeciesProfileDialog("failed-photo", "Turdus merula") {} }
        } }
        wait("profile-photo-error"); compose.onNodeWithTag("profile-fallback").assertExists()
        compose.onNodeWithTag("profile-wikipedia-image").assertDoesNotExist()
        fail = false
        compose.onNodeWithTag("profile-photo-retry").performScrollTo().performClick(); wait("profile-wikipedia-image")
        compose.onNodeWithTag("profile-photo-error").assertDoesNotExist()
        compose.onNodeWithTag("profile-fallback").assertDoesNotExist()
    }
}
