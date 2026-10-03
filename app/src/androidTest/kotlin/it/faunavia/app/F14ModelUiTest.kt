package it.faunavia.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import androidx.test.espresso.Espresso.closeSoftKeyboard

@RunWith(AndroidJUnit4::class)
class F14ModelUiTest {
    @get:Rule val compose = createAndroidComposeRule<RouteTestActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store get() = (context.applicationContext as FaunaviaApplication).personalModels
    private fun wait(tag: String) { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }

    @Test fun modelRendersVisiblePixelsRotatesZoomsAndReleasesNativeViewWithVersionedSignature() {
        val loaded = runBlocking { store.load("native-test", "Turdus merula")!! }
        var surface: ModelSurface? = null
        var ready = false
        var show by mutableStateOf(true)
        var playing by mutableStateOf(false)
        var selected by mutableIntStateOf(0)
        val start = android.os.SystemClock.elapsedRealtime()
        compose.setContent { FaunaviaTheme {
            val owner = LocalLifecycleOwner.current
            if (show) AndroidView(factory = { ModelSurface(it, loaded, owner, { ready = true }, { fail(it) }).also { value -> surface = value } },
                update = { it.control(selected, playing, 0) }, modifier = Modifier.fillMaxWidth().height(360.dp).testTag("native-model"))
        } }
        compose.waitUntil(20_000) { ready }
        compose.waitUntil(10_000) { (surface?.renderedFrames ?: 0) >= 12 }
        val firstFrameMs = android.os.SystemClock.elapsedRealtime() - start
        assertTrue("First frame exceeded 20 seconds on emulator", firstFrameMs < 20_000)
        val bitmap = compose.runOnIdle { surface!!.bitmap!! }
        var dark = 0
        var yellow = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(pixel); val g = android.graphics.Color.green(pixel); val b = android.graphics.Color.blue(pixel)
            if (r < 110 && g < 120 && b < 130) dark++
            if (r > 110 && r > g * 1.15 && g > b * 1.4) yellow++
        }
        val golden = org.json.JSONObject(instrumentation.context.assets.open("golden/model-v1.json").bufferedReader().use { it.readText() })
        val fraction = dark.toDouble() / ((bitmap.width + 1) / 2 * ((bitmap.height + 1) / 2))
        assertTrue("Rendered silhouette fraction $fraction", fraction in golden.getDouble("darkMin")..golden.getDouble("darkMax"))
        assertTrue("Beak/eye-ring pixels missing", yellow >= golden.getInt("yellowMin"))
        compose.onNodeWithTag("native-model").performTouchInput {
            swipe(center, center.copy(x = center.x + width * .18f), durationMillis = 300)
            pinch(center.copy(x = center.x - 25), center.copy(x = center.x + 25), center.copy(x = center.x - 70), center.copy(x = center.x + 70), 300)
        }
        compose.runOnIdle { selected = 1; playing = true }
        val nativeSurface = requireNotNull(surface)
        val frameCount = nativeSurface.renderedFrames
        compose.waitUntil(5_000) { nativeSurface.renderedFrames > frameCount + 3 }
        compose.runOnIdle { playing = false }
        val evidenceDirectory = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.filesDir.resolve("golden-output").path).apply { mkdirs() }
        val output = File(evidenceDirectory, "f14-model.png")
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val metrics = File(evidenceDirectory, "f14-model-metrics.json")
        metrics.writeText(org.json.JSONObject().put("firstFrameMs", firstFrameMs).put("darkFraction", fraction)
            .put("yellowSamples", yellow).put("triangles", loaded.info.triangles).put("bytes", loaded.bytes.size)
            .put("nativeHeapBytes", android.os.Debug.getNativeHeapAllocatedSize()).toString())
        bitmap.recycle()
        compose.runOnIdle { show = false }
        compose.waitUntil(5_000) { !nativeSurface.isAttachedToWindow }
        val stopped = nativeSurface.renderedFrames
        compose.waitForIdle()
        assertEquals(stopped, nativeSurface.renderedFrames)
    }
    @Test fun clipSelectionPauseSavedStateAndReturningToProfileKeepFallbackAccessible() {
        val tester = StateRestorationTester(compose)
        tester.setContent { FaunaviaTheme { SpeciesProfileAction("f14-ui", "Turdus merula") } }
        compose.onNodeWithTag("species-profile-f14-ui").performClick()
        wait("profile-version")
        click("profile-model-open")
        wait("model-surface")
        compose.waitUntil(20_000) { compose.onNodeWithTag("model-status").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { it.text.contains("pronto") } }
        click("model-clip-1")
        click("model-play")
        compose.onNodeWithTag("model-play").assertTextContains("Pausa")
        tester.emulateSavedInstanceStateRestore()
        wait("model-surface")
        compose.onNodeWithTag("model-clip-1").assertIsSelected()
        compose.onNodeWithTag("model-play").assertTextContains("Pausa")
        click("model-play")
        compose.onNodeWithTag("model-play").assertTextContains("Riproduci")
        compose.onNodeWithTag("model-close").performClick()
        compose.onNodeWithTag("profile-fallback").assertExists()
        compose.onNodeWithTag("profile-close").performClick()
        assertTrue(runBlocking { store.repository.all().isEmpty() })
    }
    @Test fun unavailablePersonalFileProvidesRetryRemovalAndIntactProfile() {
        val local = (context.applicationContext as FaunaviaApplication).repositories
        val path = "models/00000000-0000-0000-0000-000000000014/model.glb"
        runBlocking { local.personalModels.save(it.faunavia.domain.PersonalModel("f14-corrupt", "Testus syntheticus", path,
            "a".repeat(64), 100, "Synthetic fixture", "Local test", "Original test", "None", f8bNow)) }
        compose.setContent { FaunaviaTheme { SpeciesProfileAction("f14-corrupt", "Testus syntheticus") } }
        compose.onNodeWithTag("species-profile-f14-corrupt").performClick()
        wait("profile-version"); click("profile-model-open"); wait("model-error")
        compose.onNodeWithTag("model-fallback").assertExists()
        click("model-retry"); wait("model-error")
        click("model-remove"); compose.onNodeWithTag("model-remove-confirm").performClick()
        wait("model-unavailable")
        compose.onNodeWithTag("model-close").performClick()
        compose.onNodeWithTag("profile-fallback").assertExists()
        assertNull(runBlocking { local.personalModels.get("f14-corrupt") })
    }
    @Test fun realDocumentPickerImportsAttributedStaticModelAndRemovalKeepsSpeciesProfile() {
        val device = UiDevice.getInstance(instrumentation)
        val bytes = modelFixture { it.remove("animations") }
        // Publish an owned document through MediaStore; UiAutomation shell commands do not interpret redirections.
        val fixture = requireNotNull(context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "faunavia-f14-fixture.glb")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "model/gltf-binary")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        requireNotNull(context.contentResolver.openOutputStream(fixture)).use { it.write(bytes) }
        context.contentResolver.update(fixture, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        try {
            compose.setContent { FaunaviaTheme { SpeciesProfileAction("f14-picker", "Testus syntheticus") } }
            compose.onNodeWithTag("species-profile-f14-picker").performClick()
            wait("profile-version"); click("profile-model-open"); wait("model-unavailable"); click("model-import-form")
            for ((tag, value) in listOf("model-author" to "Synthetic fixture", "model-source" to "Original local test", "model-license" to "Project original test asset")) {
                compose.onNodeWithTag(tag).performScrollTo().performTextInput(value)
                closeSoftKeyboard()
            }
            click("model-rights"); click("model-pick")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.documentsui")), 10_000))
            device.findObject(By.desc(Pattern.compile("(?i).*(show roots|open navigation).*")))?.click()
                ?: device.findObject(By.res("android:id/home"))?.click()
            device.waitForIdle()
            assertTrue(device.wait(Until.hasObject(By.text("Downloads")), 5_000))
            device.waitForIdle()
            device.findObject(By.text("Downloads"))?.click()
            device.waitForIdle()
            val documentVisible = device.wait(Until.hasObject(By.text("faunavia-f14-fixture.glb")), 10_000)
            if (!documentVisible) {
                val diagnostic = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.filesDir.path).apply { mkdirs() }
                device.takeScreenshot(File(diagnostic, "f14-picker.png"))
                device.dumpWindowHierarchy(File(diagnostic, "f14-picker.xml"))
            }
            assertTrue("GLB must be selected by the real SAF provider", documentVisible)
            device.waitForIdle()
            requireNotNull(device.findObject(By.text("faunavia-f14-fixture.glb"))).click()
            wait("model-static")
            val saved = runBlocking { store.repository.get("f14-picker")!! }
            assertEquals(sha256(bytes), saved.sha256)
            assertEquals("Synthetic fixture", saved.author)
            assertTrue(store.file(saved.relativePath).isFile)
            click("model-remove"); compose.onNodeWithTag("model-remove-confirm").performClick()
            wait("model-unavailable")
            compose.onNodeWithTag("model-close").performClick()
            compose.onNodeWithTag("profile-fallback").assertExists()
            assertNull(runBlocking { store.repository.get("f14-picker") })
        } finally { context.contentResolver.delete(fixture, null, null) }
    }
}
