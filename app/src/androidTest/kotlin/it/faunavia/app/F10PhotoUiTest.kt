package it.faunavia.app

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import it.faunavia.domain.*
import it.faunavia.local.*
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
class F10PhotoUiTest {
    @get:Rule val compose = createAndroidComposeRule<AnalysisTestActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "f10-photo-ui.db"
    private val root = File(context.noBackupFilesDir, "f10-ui")
    private val source = File(context.cacheDir, "f10-ui-source.jpg")
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private lateinit var store: PrivatePhotoStore
    private lateinit var manager: MemoryPhotos
    private var published: Uri? = null
    private val zone = ZoneId.of("Europe/Rome")

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name)
        root.mkdirs()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
        local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
        local.diary.create(ObservationDraft("o", f8bTaxon.id, f8bNow, zone, notes = "senza perdere testo"))
        photoFixture(source, 6)
        store = PrivatePhotoStore(context, root)
        manager = MemoryPhotos(store, local.diary, local.unidentified)
    }

    @After fun cleanup() {
        AnalysisTestActivity.testContent = null
        published?.let { context.contentResolver.delete(it, null, null) }
        db.close(); context.deleteDatabase(name); root.deleteRecursively(); source.delete()
    }

    private fun show() = compose.activityRule.scenario.onActivity { activity ->
        activity.showTestContent {
            FaunaviaTheme { DiaryScreen(local.diary, local.catalogue, F8BOfflineTaxonomy(),
                unidentifiedRepository = local.unidentified, deviceZone = zone, now = { f8bNow }, photoManager = manager) }
        }
    }

    private fun open(id: String) {
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-photos-$id")) }.isSuccess
        }
        compose.onNodeWithTag("diary-photos-$id").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-empty").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("photo-image-${runBlocking { manager.list(id).firstOrNull()?.id }}").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun publishPhoto(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "faunavia-f10-synthetic.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FaunaviaTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri)!!.use { it.write(source.readBytes()) }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        published = uri
        return uri
    }

    @Test fun realPhotoPickerImportsAPrivateImageWithoutBroadStoragePermissions() {
        publishPhoto()
        show()
        open("o")
        compose.onNodeWithTag("photo-add").performClick()
        val device = UiDevice.getInstance(instrumentation)
        val imageSelector = By.desc(Pattern.compile("(?i).*(photo taken|image taken|photo,|image,).*"))
        val image = device.wait(Until.findObject(imageSelector), 10_000)
            ?: device.wait(Until.findObject(By.res(Pattern.compile(".*:id/(icon_thumbnail|thumbnail|preview_image).*"))), 5_000)
        if (image == null) {
            val dump = File(context.cacheDir, "f10-picker-hierarchy.xml")
            device.dumpWindowHierarchy(dump)
            fail("Photo Picker image missing: ${dump.readText()}")
        }
        image!!.click()
        val add = device.wait(Until.findObject(By.text(Pattern.compile("(?i)^(add|aggiungi|done|fine).*$"))), 5_000)
        add?.click()
        compose.waitUntil(20_000) { runBlocking { local.diary.photos("o").size == 1 } }
        val photo = runBlocking { local.diary.photos("o").single() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-image-${photo.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("photo-count").assertTextEquals("1 foto")
        val rendered = compose.onNodeWithTag("photo-image-${photo.id}").captureToImage().toPixelMap()
        assertTrue(rendered.width > 20 && rendered.height > 20)
        // Image uses Fit: portrait photos have horizontal padding inside the wide card.
        val fit = minOf(rendered.width.toDouble() / photo.width, rendered.height.toDouble() / photo.height)
        val sampleX = ((rendered.width - photo.width * fit) / 2 + photo.width * fit / 4).toInt()
        val sampleY = ((rendered.height - photo.height * fit) / 2 + photo.height * fit / 4).toInt()
        val color = rendered[sampleX, sampleY]
        assertTrue("Gallery renders the rotated green corner", color.green > 0.7f && color.red < 0.3f)
        val outputPath = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val output = (outputPath?.let(::File) ?: File(context.filesDir, "golden-output")).apply { mkdirs() }
        File(output, "f10-gallery-actual.png").outputStream().use {
            compose.onNodeWithTag("photo-gallery").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(1, photo.orientation)
        assertTrue(photo.height > photo.width)
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(declared.any { it.contains("READ_MEDIA") || it.contains("EXTERNAL_STORAGE") || it.contains("CAMERA") })
        context.contentResolver.delete(published!!, null, null); published = null
        assertNotNull(store.load(photo, false))
        assertEquals("senza perdere testo", runBlocking { local.diary.get("o")!!.notes })
    }

    @Test fun albumAndPrivateImagesSurviveRealActivityRecreationAndPhotoRemovalKeepsText() {
        val photo = runBlocking { manager.attach("o", publishPhoto()) }
        show(); open("o")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-image-${photo.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("photo-count").assertTextEquals("1 foto")
        compose.onNodeWithTag("photo-list").performScrollToNode(hasTestTag("photo-delete-${photo.id}"))
        compose.onNodeWithTag("photo-delete-${photo.id}").performClick()
        compose.onNodeWithTag("photo-list").performScrollToNode(hasTestTag("photo-delete-confirm-${photo.id}"))
        compose.onNodeWithTag("photo-delete-confirm-${photo.id}").performClick()
        compose.waitUntil(10_000) { runBlocking { local.diary.photos("o").isEmpty() } }
        assertEquals("senza perdere testo", runBlocking { local.diary.get("o")!!.notes })
        assertFalse(store.file(photo.relativePath).exists())
    }

    @Test fun missingPhotoHasVisibleFallbackAndCanBeRemovedWithoutLosingTheObservation() {
        val photo = runBlocking { manager.attach("o", publishPhoto()) }
        store.file(photo.relativePath).delete()
        show()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-photos-o")) }.isSuccess }
        compose.onNodeWithTag("diary-photos-o").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-unavailable-${photo.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("photo-list").performScrollToNode(hasTestTag("photo-unavailable-${photo.id}"))
        compose.onNodeWithTag("photo-unavailable-${photo.id}").assertTextContains("Foto mancante", substring = true)
        compose.onNodeWithTag("photo-list").performScrollToNode(hasTestTag("photo-delete-${photo.id}"))
        compose.onNodeWithTag("photo-delete-${photo.id}").performClick()
        compose.onNodeWithTag("photo-list").performScrollToNode(hasTestTag("photo-delete-confirm-${photo.id}"))
        compose.onNodeWithTag("photo-delete-confirm-${photo.id}").performClick()
        compose.waitUntil(10_000) { runBlocking { local.diary.photos("o").isEmpty() } }
        assertNotNull(runBlocking { local.diary.get("o") })
    }

    @Test fun identifyingADraftThroughDiaryPreservesItsPrivatePhotos() {
        runBlocking { local.unidentified.save(UnidentifiedInput("d", f8bNow, zone, notes = "bozza con foto")) }
        val photo = runBlocking { manager.attach("d", publishPhoto()) }
        show(); open("d")
        compose.onNodeWithTag("photo-close").performClick()
        compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-draft-edit-d"))
        compose.onNodeWithTag("diary-draft-edit-d").performClick()
        compose.onNodeWithTag("diary-editor").performScrollToNode(hasTestTag("diary-taxon-query"))
        compose.onNodeWithTag("diary-taxon-query").performTextReplacement("Merlo")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("diary-editor").performScrollToNode(hasTestTag("diary-taxon-result-${f8bTaxon.id}")) }.isSuccess }
        compose.onNodeWithTag("diary-taxon-result-${f8bTaxon.id}").performClick()
        compose.onNodeWithTag("diary-editor").performScrollToNode(hasTestTag("diary-save"))
        compose.onNodeWithTag("diary-save").performClick()
        compose.waitUntil(10_000) { runBlocking { local.diary.get("d") != null } }
        assertNull(runBlocking { local.unidentified.get("d") })
        assertEquals(listOf(photo), runBlocking { local.diary.photos("d") })
        assertEquals("bozza con foto", runBlocking { local.diary.get("d")!!.notes })
    }

    @Test fun recordDeletionKeepsFileCleanupFailureVisibleAfterTheListReloads() {
        val photo = runBlocking { manager.attach("o", publishPhoto()) }
        val directory = store.file(photo.relativePath).parentFile!!
        show()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-delete-o")) }.isSuccess }
        assertTrue(directory.setWritable(false, false))
        try {
            compose.onNodeWithTag("diary-delete-o").performClick()
            compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-delete-confirm-o"))
            compose.onNodeWithTag("diary-delete-confirm-o").performClick()
            compose.waitUntil(10_000) { runBlocking { local.diary.get("o") == null } }
            compose.onNodeWithTag("diary-list").performScrollToNode(hasTestTag("diary-list-error"))
            compose.onNodeWithTag("diary-list-error").assertTextContains("attendono la pulizia", substring = true)
            assertTrue(store.file(photo.relativePath).exists())
            assertTrue(runBlocking { local.diary.photos("o").isEmpty() })
        } finally { directory.setWritable(true, true) }
    }
}
