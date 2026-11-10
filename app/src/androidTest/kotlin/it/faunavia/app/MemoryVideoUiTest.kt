package it.faunavia.app

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import it.faunavia.domain.ObservationDraft
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.testing.FakeClock
import java.io.File
import java.time.ZoneId
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryVideoUiTest {
    @get:Rule val compose = createAndroidComposeRule<AnalysisTestActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "memory-video-ui.db"
    private val root = File(context.noBackupFilesDir, "video-ui-test")
    private val source = File(context.cacheDir, "video-ui-source.mp4")
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private lateinit var photos: PrivatePhotoStore
    private lateinit var store: PrivateVideoStore
    private lateinit var manager: MemoryVideos
    private var published: Uri? = null

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name); root.mkdirs()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
        local.catalogue.saveTaxon(f8bTaxon)
        local.diary.create(ObservationDraft("o", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome"), notes = "conservato"))
        photos = PrivatePhotoStore(context, root)
        store = PrivateVideoStore(context, photos, root, { Long.MAX_VALUE })
        manager = MemoryVideos(store, local.videos, photos)
        videoFixture(source)
    }
    @After fun cleanup() {
        AnalysisTestActivity.testContent = null
        published?.let { context.contentResolver.delete(it, null, null) }
        db.close(); context.deleteDatabase(name); root.deleteRecursively(); source.delete()
    }
    private fun show() = compose.activityRule.scenario.onActivity { activity ->
        activity.showTestContent { FaunaviaTheme { MemoryVideoSection("o", manager) } }
    }
    private fun model(): MemoryVideoModel = ViewModelProvider(compose.activity).get("video-o", MemoryVideoModel::class.java)

    private fun selectDirectly() {
        compose.waitUntil(10_000) { !model().busy }
        compose.runOnUiThread { model().select(Uri.fromFile(source), manager) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("video-editor").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun realPickerTrimmingPreviewAndSelectionSurviveRecreationThenSaveOnePrivateVideo() {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "faunavia-synthetic-video.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FaunaviaTest")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        published = requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(published!!)!!.use { it.write(source.readBytes()) }
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(published!!, values, null, null)
        show()
        compose.onNodeWithTag("video-add").performClick()
        val device = UiDevice.getInstance(instrumentation)
        val video = device.wait(Until.findObject(By.desc(Pattern.compile("(?i).*video.*"))), 10_000)
            ?: device.wait(Until.findObject(By.res(Pattern.compile(".*:id/(icon_thumbnail|thumbnail|preview_image).*"))), 5_000)
        assertNotNull("The real picker must expose the synthetic video", video)
        video!!.click()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("video-editor").fetchSemanticsNodes().isNotEmpty() }
        val thumbs = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true)
        thumbs[0].performSemanticsAction(SemanticsActions.SetProgress) { it(2350f) }
        thumbs[1].performSemanticsAction(SemanticsActions.SetProgress) { it(3650f) }
        compose.onNodeWithTag("video-range-label").assertTextEquals("Inizio 00:02.350 · Fine 00:03.650")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("video-range-label").assertTextEquals("Inizio 00:02.350 · Fine 00:03.650")
        compose.waitUntil(15_000) { !compose.onNodeWithTag("video-preview-play").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        compose.onNodeWithTag("video-preview-play").performClick()
        val outputPath = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val output = (outputPath?.let(::File) ?: File(context.filesDir, "golden-output")).apply { mkdirs() }
        val capture = File(output, "video-editor.png")
        // Semantics can be ready before the recreated native surface has drawn its first frame.
        // Require actual green video pixels, beyond the much smaller green action buttons.
        compose.waitUntil(15_000) {
            device.takeScreenshot(capture)
            BitmapFactory.decodeFile(capture.absolutePath)?.let { bitmap ->
                var green = 0
                var total = 0
                for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.green(color) > Color.red(color) + 50 && Color.green(color) > Color.blue(color) + 50) green++
                    total++
                }
                bitmap.recycle()
                green > total / 20
            } == true
        }
        compose.onNodeWithTag("video-save").performScrollTo().performClick()
        compose.waitUntil(60_000) { runBlocking { manager.get("o") != null } }
        compose.onNodeWithTag("video-add").assertTextEquals("Sostituisci video")
        assertEquals(1, runBlocking { local.videos.all().size })
        val saved = runBlocking { requireNotNull(manager.get("o")) }
        assertTrue(saved.durationMs in 1200..1450)
        context.contentResolver.delete(published!!, null, null); published = null
        source.delete()
        compose.onNodeWithTag("video-open").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("video-preview").fetchSemanticsNodes().isNotEmpty() }
        VideoInspection.validate(saved, store.file(saved.relativePath))
        compose.onNodeWithText("Chiudi video").performClick()
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
    }

    @Test fun diaryShowsTheSavedVideoOpeningFrameForObservationsAndDrafts() {
        runBlocking {
            manager.attach("o", store.stage(Uri.fromFile(source)), it.faunavia.domain.VideoTrim(2200, 3200)) {}
            local.unidentified.save(it.faunavia.domain.UnidentifiedInput("d", f8bNow, ZoneId.of("Europe/Rome")))
            local.videos.save(requireNotNull(manager.get("o")).copy(memoryId = "d"))
        }
        compose.activityRule.scenario.onActivity { activity ->
            activity.showTestContent { FaunaviaTheme {
                DiaryScreen(local.diary, local.catalogue, F8BOfflineTaxonomy(), unidentifiedRepository = local.unidentified,
                    tripRepository = local.trips, photoManager = MemoryPhotos(photos, local.diary, local.unidentified, manager))
            } }
        }
        listOf("d", "o").forEach { id ->
            if (id == "o") {
                compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-species-${f8bTaxon.id}"))
                compose.onNodeWithTag("diary-species-${f8bTaxon.id}").performClick()
            }
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-media-$id")) }.isSuccess
            }
            compose.waitUntil(10_000) {
                val pixels = compose.onNodeWithTag("diary-media-$id").captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, pixels.height / 2]
                color.green > 0.6f && color.red < 0.4f && color.blue < 0.4f
            }
            compose.onNodeWithTag("diary-media-video-$id", useUnmergedTree = true).assertIsDisplayed()
        }
        compose.onNodeWithTag("diary-media-o").performClick()
        compose.onNodeWithTag("photo-gallery").assertIsDisplayed()
        compose.onNodeWithTag("photo-close").performClick()
        assertEquals("conservato", runBlocking { local.diary.get("o")!!.notes })
    }

    @Test fun cancelSelectionAndRemoveVideoKeepTextAndPriorAttachment() {
        runBlocking { manager.attach("o", store.stage(Uri.fromFile(source)), it.faunavia.domain.VideoTrim(0, 1000)) {} }
        val saved = runBlocking { manager.get("o") }
        show(); selectDirectly()
        compose.onNodeWithTag("video-discard").performScrollTo().performClick()
        assertEquals(saved, runBlocking { manager.get("o") })
        assertTrue(store.file(saved!!.relativePath).exists())
        compose.onNodeWithTag("video-remove").performClick()
        compose.onNodeWithTag("video-remove-confirm").performClick()
        compose.waitUntil(10_000) { runBlocking { manager.get("o") == null } }
        assertEquals("conservato", runBlocking { local.diary.get("o")?.notes })
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
    }
}
