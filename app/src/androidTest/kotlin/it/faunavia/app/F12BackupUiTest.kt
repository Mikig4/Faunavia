package it.faunavia.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import it.faunavia.domain.*
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F12BackupUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val application get() = context.applicationContext as FaunaviaApplication
    private var exportedName: String? = null
    private val temporary = File(context.cacheDir, "f12-ui-input.zip")
    @After fun cleanup() {
        exportedName?.let { device.executeShellCommand("rm /sdcard/Download/$it") }
        temporary.delete()
    }
    private fun settings() {
        compose.onNodeWithTag("destination-bar").performScrollToIndex(3)
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("backup-export"))
    }
    private fun model(): BackupViewModel {
        var result: BackupViewModel? = null
        compose.activityRule.scenario.onActivity { result = ViewModelProvider(it)["local-backup", BackupViewModel::class.java] }
        return requireNotNull(result)
    }
    private fun <T> whileUiAdvances(block: suspend () -> T): T {
        // A resumed Settings coroutine can hold the preferences mutex. Keep advancing Compose's
        // main dispatcher while the fixture mutation waits for it, rather than blocking the test.
        val task = CoroutineScope(Dispatchers.IO).async { block() }
        try {
            compose.waitUntil(30_000) { task.isCompleted }
            return runBlocking { task.await() }
        } finally { task.cancel() }
    }
    private fun downloads() {
        device.wait(Until.hasObject(By.pkg("com.android.documentsui")), 10_000)
        device.waitForIdle()
        val drawer = device.findObject(By.desc(Pattern.compile("(?i).*(show roots|open navigation).*")))
            ?: device.findObject(By.res("android:id/home"))
        drawer?.click()
        device.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.text("Downloads")), 5_000))
        device.findObject(By.text("Downloads"))?.click()
        device.waitForIdle()
    }

    @Test fun realSafExportImportPreviewRotationAndRestoreKeepPhotosAndRescheduleReminder() {
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val local = application.repositories
        val zone = ZoneId.of("Europe/Rome")
        val photo = runBlocking {
        local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
        local.diary.create(ObservationDraft("saved", f8bTaxon.id, f8bNow, zone, notes = "Ricordo recuperabile"))
        local.trips.save(f8bTrip())
        local.wishlist.add(f8bTaxon.id)
        val source = File(context.cacheDir, "f12-ui-image.jpg")
        photoFixture(source)
        val attached = MemoryPhotos(application.privatePhotos, local.diary, local.unidentified).attach("saved", Uri.fromFile(source))
        source.delete()
        application.reminderPreferences.save(true, LocalTime.MIDNIGHT)
        attached
        }
        val work = WorkManager.getInstance(context)
        val beforeWork = work.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().last().id
        settings()
        val retainedModel = model()
        compose.onNodeWithTag("backup-export").performClick()
        downloads()
        val filename = "faunavia-f12-${UUID.randomUUID()}.zip"
        exportedName = filename
        val field = device.wait(Until.findObject(By.res("com.android.documentsui:id/title")), 10_000)
            ?: device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
        assertNotNull("Storage Access Framework must offer a filename", field)
        field!!.text = filename
        val save = device.wait(Until.findObject(By.text(Pattern.compile("(?i)^save$"))), 10_000)
        assertNotNull("System document picker Save button", save)
        save!!.click()
        compose.waitUntil(20_000) { retainedModel.message?.startsWith("Backup creato") == true && !retainedModel.busy }
        val bytes = android.util.Base64.decode(device.executeShellCommand("base64 /sdcard/Download/$filename"), android.util.Base64.DEFAULT)
        whileUiAdvances {
        assertNotNull(application.localBackup.prepare(java.io.ByteArrayInputStream(bytes)).also { it.discard() })
        local.diary.delete("saved")
        local.diary.create(ObservationDraft("old", f8bTaxon.id, f8bNow, zone, notes = "Da sostituire"))
        application.reminderPreferences.save(false, LocalTime.of(20, 30))
        }
        compose.onNodeWithTag("backup-import").performClick()
        downloads()
        val selected = device.wait(Until.findObject(By.text(filename)), 10_000)
        assertNotNull("The exported backup must be selectable through SAF", selected)
        selected!!.click()
        compose.waitUntil(20_000) { retainedModel.prepared != null && !retainedModel.busy }
        compose.onNodeWithTag("backup-preview").assertTextContains("1 avvistamento", substring = true)
        val screenshots = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.filesDir.absolutePath).apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(screenshots, "f12-backup-preview.png")))
        assertNotNull(runBlocking { local.diary.get("old") })
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("backup-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("backup-confirm").performClick()
        assertNotNull(device.wait(Until.findObject(By.text("Ripristino completato")), 20_000))
        whileUiAdvances {
        assertNull(local.diary.get("old"))
        assertEquals("Ricordo recuperabile", local.diary.get("saved")!!.notes)
        val restored = local.diary.photos("saved").single()
        assertEquals(photo.id, restored.id)
        assertNotNull(application.privatePhotos.load(restored, true)?.also { it.recycle() })
        assertTrue(local.settings.get().reminderEnabled)
        }
        assertNotEquals(beforeWork, work.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().last().id)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES))
    }

    @Test fun invalidImportShowsRecoverableErrorAndCancellingValidPreviewKeepsCurrentDiary() = runBlocking<Unit> {
        val local = application.repositories
        local.catalogue.saveTaxon(f8bTaxon)
        local.diary.create(ObservationDraft("old", f8bTaxon.id, f8bNow, ZoneId.of("UTC"), notes = "Da conservare"))
        settings()
        val model = model()
        temporary.writeBytes(byteArrayOf(1, 2, 3))
        compose.runOnIdle { model.prepare(Uri.fromFile(temporary)) }
        compose.waitUntil(10_000) { !model.busy && model.failed }
        compose.onNodeWithTag("backup-message").assertTextContains("backup Faunavia valido", substring = true)
        assertNotNull(local.diary.get("old"))
        temporary.outputStream().use { application.localBackup.export(it) }
        compose.runOnIdle { model.prepare(Uri.fromFile(temporary)) }
        compose.waitUntil(20_000) { !model.busy && model.prepared != null }
        compose.onNodeWithTag("backup-dismiss").performClick()
        compose.waitUntil(10_000) { !model.busy && model.prepared == null }
        assertEquals("Da conservare", local.diary.get("old")!!.notes)
        compose.onNodeWithTag("backup-message").assertTextContains("annullata", substring = true)
    }
}
