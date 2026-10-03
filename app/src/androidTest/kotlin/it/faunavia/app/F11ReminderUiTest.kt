package it.faunavia.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import it.faunavia.domain.*
import it.faunavia.testing.FakeClock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F11ReminderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val application get() = context.applicationContext as FaunaviaApplication

    private fun screenshot(tag: String, name: String) {
        val directory = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.filesDir.absolutePath).apply { mkdirs() }
        File(directory, name).outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun settings() {
        compose.onNodeWithTag("destination-bar").performScrollToIndex(3)
        compose.onNodeWithTag("nav-settings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reminder-save").fetchSemanticsNodes().isNotEmpty() &&
            runCatching { compose.onNodeWithTag("reminder-save").assertIsEnabled() }.isSuccess }
    }

    @Test fun savesTimeRejectsInvalidInputAndDeniedPermissionLeavesDiaryAvailable() {
        settings()
        compose.onNodeWithTag("reminder-time").performTextReplacement("24:00")
        compose.onNodeWithTag("reminder-save").assertIsNotEnabled()
        compose.onNodeWithTag("reminder-time").performTextReplacement("20:30")
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("reminder-save"))
        compose.onNodeWithTag("reminder-save").performClick()
        compose.waitUntil(10_000) { runBlocking { application.repositories.settings.get().reminderTime == LocalTime.of(20, 30) } }
        // Saving can already dismiss the IME. A Back event would then leave Settings.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("reminder-enabled"))
        compose.onNodeWithTag("reminder-enabled").performClick()
        val deny = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 10_000)
        assertNotNull("Android must request notification permission only on activation.", deny)
        deny!!.click()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("reminder-enabled").assertIsOff().assertIsEnabled() }.isSuccess }
        assertFalse(runBlocking { application.repositories.settings.get().reminderEnabled })
        compose.onNodeWithTag("destination-bar").performScrollToIndex(1)
        compose.onNodeWithTag("nav-diary").performClick()
        compose.onNodeWithTag("screen-diary").assertIsDisplayed()
    }

    @Test fun actualNotificationTapOpensLocalSpeciesAndPhotosAndSurvivesRecreation() = runBlocking<Unit> {
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        application.reminderPreferences.reconcile()
        val local = application.repositories
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
        local.diary.create(ObservationDraft("f11-photo", f8bTaxon.id, now, zone, notes = "ricordo del giorno", quantity = 3))
        local.unidentified.save(UnidentifiedInput("f11-draft", now, zone, notes = "non contare"))
        val source = File(context.cacheDir, "f11-source.jpg")
        photoFixture(source)
        val photos = MemoryPhotos(application.privatePhotos, local.diary, local.unidentified)
        val photo = photos.attach("f11-photo", Uri.fromFile(source))
        local.settings.save(AppSettings(true, LocalTime.MIDNIGHT, zone))
        val runner = ReminderRunner(local.dailyReminder, FakeClock(now.toEpochMilli()), { zone },
            application.summaryNotifications::allowed, application.summaryNotifications::post)
        val worker = TestListenableWorkerBuilder<DailyReminderWorker>(context).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: android.content.Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                DailyReminderWorker(appContext, workerParameters, runner::run)
        }).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.size)
        device.openNotification()
        val notification = device.wait(Until.findObject(By.textContains("Hai registrato 1 avvistamento oggi")), 10_000)
        assertNotNull("The real system notification must be visible.", notification)
        notification!!.click()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("screen-daily-summary").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("summary-count").assertTextEquals("1 avvistamento · 1 specie")
        compose.onNodeWithText("Merlo").assertIsDisplayed()
        screenshot("screen-daily-summary", "f11-summary.png")
        // ActivityScenario filters lifecycle events by its original launcher intent. The tap changes that
        // signature via onNewIntent; restore only the harness signature after the link was consumed.
        var previousActivity: MainActivity? = null
        compose.activityRule.scenario.onActivity { activity ->
            previousActivity = activity
            activity.intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            activity.recreate()
        }
        compose.waitUntil(10_000) { runCatching { compose.activity !== previousActivity }.getOrDefault(false) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("summary-photos-f11-photo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("summary-content").performScrollToNode(hasTestTag("summary-photos-f11-photo"))
        compose.onNodeWithTag("summary-photos-f11-photo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-image-${photo.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("photo-count").assertTextEquals("1 foto")
        compose.onNodeWithTag("photo-close").performClick()
        compose.onNodeWithTag("summary-content").performScrollToNode(hasTestTag("summary-back"))
        compose.onNodeWithTag("summary-back").performClick()
        compose.onNodeWithTag("screen-diary").assertIsDisplayed()
        assertEquals("ricordo del giorno", local.diary.get("f11-photo")!!.notes)
        assertEquals("non contare", local.unidentified.get("f11-draft")!!.input.notes)
        settings()
        screenshot("screen-settings", "f11-settings.png")
        source.delete()
    }

    @Test fun grantingPermissionEnablesReminderAndDisablingRemovesTheUniqueJob() {
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        settings()
        compose.onNodeWithTag("reminder-enabled").performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("reminder-enabled").assertIsOn().assertIsEnabled() }.isSuccess }
        assertTrue(runBlocking { application.repositories.settings.get().reminderEnabled })
        val work = androidx.work.WorkManager.getInstance(context)
        assertEquals(1, work.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().count { !it.state.isFinished })
        screenshot("screen-settings", "f11-settings.png")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("reminder-enabled").assertIsOn().assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reminder-enabled").performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("reminder-enabled").assertIsOff().assertIsEnabled() }.isSuccess }
        assertFalse(runBlocking { application.repositories.settings.get().reminderEnabled })
        assertTrue(work.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().none { !it.state.isFinished })
    }

    @Test fun summaryWithoutPermissionShowsEmptyStateAndMalformedIntentFallsBackToApp() {
        settings()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("summary-today"))
        compose.onNodeWithTag("summary-today").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("summary-empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("summary-empty").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            assertNull(SummaryLink.read(Intent().putExtra("it.faunavia.summary.DATE", "invalid")))
            assertEquals(SummaryLink(LocalDate.now(), ZoneId.systemDefault()),
                SummaryLink.read(SummaryLink(LocalDate.now(), ZoneId.systemDefault()).intent(activity)))
        }
    }
}
