package it.faunavia.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import it.faunavia.domain.AppSettings
import it.faunavia.domain.SettingsRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F11ScheduleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val work = WorkManager.getInstance(context)
    private val now = Instant.parse("2026-10-02T08:00:00Z")
    private var zone = ZoneId.of("Europe/Rome")
    private val scheduler = ReminderScheduler(work, { now }, { zone })
    private fun active() = work.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().filter { !it.state.isFinished }

    @Before fun settleStartup() = runBlocking<Unit> {
        (context.applicationContext as FaunaviaApplication).reminderPreferences.reconcile()
    }

    @After fun cleanup() { work.cancelUniqueWork(ReminderScheduler.WORK_NAME).result.get() }

    @Test fun uniquePeriodicWorkSurvivesReconciliationAndRealignsTimeAndTimezoneThenCancels() = runBlocking<Unit> {
        val preferences = AppSettings(true, LocalTime.of(20, 30), zone)
        scheduler.reconcile(preferences)
        val first = active().single()
        assertEquals(37_800_000L, first.initialDelayMillis)
        assertEquals(900_000L, first.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(androidx.work.NetworkType.NOT_REQUIRED, first.constraints.requiredNetworkType)
        // A new scheduler represents reopening after process loss/boot; KEEP retains the persisted work ID.
        ReminderScheduler(WorkManager.getInstance(context), { now }, { zone }).reconcile(preferences)
        assertEquals(first.id, active().single().id)
        scheduler.reconcile(preferences.copy(reminderTime = LocalTime.of(21, 0)), replace = true)
        val changed = active().single()
        assertNotEquals(first.id, changed.id)
        assertEquals(39_600_000L, changed.initialDelayMillis)
        zone = ZoneId.of("America/New_York")
        scheduler.reconcile(preferences.copy(zoneId = zone), replace = true)
        assertEquals(59_400_000L, active().single().initialDelayMillis)
        scheduler.reconcile(preferences.copy(reminderEnabled = false))
        assertTrue(active().isEmpty())
    }

    @Test fun leavingSettingsDuringSaveStillInstallsThePersistedReminder() = runBlocking<Unit> {
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val saved = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        var stored = AppSettings(zoneId = zone)
        val repository = object : SettingsRepository {
            override suspend fun get() = stored
            override suspend fun save(settings: AppSettings) {
                stored = settings
                saved.complete(Unit)
                releaseSave.await()
            }
        }
        val preferences = ReminderPreferences(repository, scheduler, DailySummaryNotifications(context), { zone })
        val saving = launch { preferences.save(true, LocalTime.of(20, 30)) }
        saved.await()
        saving.cancel() // The UI scope disappears after persistence but before scheduling.
        releaseSave.complete(Unit)
        saving.join()
        assertTrue(stored.reminderEnabled)
        assertEquals(1, active().size)
    }
}
