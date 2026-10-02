package it.faunavia.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F11ReminderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "f11-reminder.db"
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private val now = Instant.parse("2026-10-02T18:30:00Z")
    private val clock = FakeClock(now.toEpochMilli())
    private val rome = ZoneId.of("Europe/Rome")
    private val time = LocalTime.of(20, 30)
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name)
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
        local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
        local.settings.save(AppSettings(true, time, rome))
    }

    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    private suspend fun observe(id: String = "o", at: Instant = now, quantity: Int = 1) =
        local.diary.create(ObservationDraft(id, f8bTaxon.id, at, ZoneId.of("Asia/Tokyo"), notes = "sentinella", quantity = quantity))

    @Test fun zeroAndDraftsNeverNotifyButIdentifiedRecordsCountIndependentlyOfQuantity() = runBlocking<Unit> {
        var posted: DailySummary? = null
        val post: (DailySummary) -> Boolean = { posted = it; true }
        assertEquals(ReminderDelivery.EMPTY, local.dailyReminder.deliver(now, rome, post))
        local.unidentified.save(UnidentifiedInput("draft", now, rome, notes = "da identificare", quantity = 8))
        assertEquals(ReminderDelivery.EMPTY, local.dailyReminder.deliver(now, rome, post))
        observe("one", quantity = 9)
        observe("two", now.minusSeconds(3_600))
        observe("yesterday", Instant.parse("2026-10-01T21:59:59Z"))
        observe("tomorrow", Instant.parse("2026-10-02T22:00:00Z"))
        local.diary.addPhoto(ObservationPhoto("sentinel-photo", "one", "photos/sentinel.jpg", "a".repeat(64), 100, "image/jpeg"))
        val diary = local.diary.list()
        val photos = local.diary.photos("one")
        val draft = local.unidentified.get("draft")
        val taxon = local.catalogue.taxon(f8bTaxon.id)
        assertEquals(ReminderDelivery.SENT, local.dailyReminder.deliver(now, rome, post))
        assertEquals(DailySummary(java.time.LocalDate.of(2026, 10, 2), rome, 2), posted)
        assertEquals(diary, local.diary.list())
        assertEquals(photos, local.diary.photos("one"))
        assertEquals(draft, local.unidentified.get("draft"))
        assertEquals(taxon, local.catalogue.taxon(f8bTaxon.id))
    }

    @Test fun beforeTimeAndDelayedPastMidnightNeverSendThePreviousDay() = runBlocking<Unit> {
        observe()
        val post: (DailySummary) -> Boolean = { fail("Unexpected notification"); false }
        assertEquals(ReminderDelivery.BEFORE_TIME, local.dailyReminder.deliver(now.minusMillis(1), rome, post))
        assertEquals(ReminderDelivery.BEFORE_TIME, local.dailyReminder.deliver(Instant.parse("2026-10-02T22:00:00Z"), rome, post))
        assertEquals(ReminderDelivery.EMPTY, local.dailyReminder.deliver(now.plusSeconds(86_400), rome, post))
        local.settings.save(AppSettings(false, time, rome))
        assertEquals(ReminderDelivery.DISABLED, local.dailyReminder.deliver(now, rome, post))
    }

    @Test fun concurrentWorkersAndReopenedDatabaseSendOnlyOnceEvenAfterTimezoneChange() = runBlocking<Unit> {
        observe()
        var count = 0
        val post: (DailySummary) -> Boolean = { count++; true }
        val results = coroutineScope { (1..3).map { async(Dispatchers.Default) { local.dailyReminder.deliver(now, rome, post) } }.awaitAll() }
        assertEquals(1, count)
        assertEquals(1, results.count { it == ReminderDelivery.SENT })
        assertEquals(2, results.count { it == ReminderDelivery.ALREADY_SENT })
        db.close()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
        local.settings.save(AppSettings(true, LocalTime.MIDNIGHT, ZoneId.of("UTC")))
        assertEquals(ReminderDelivery.ALREADY_SENT, local.dailyReminder.deliver(now, ZoneId.of("UTC"), post))
        assertEquals(1, count)
        assertEquals("sentinella", local.diary.get("o")!!.notes)
    }

    @Test fun failedPostDoesNotConsumeDayAndNextAttemptCanSucceed() = runBlocking<Unit> {
        observe()
        assertEquals(ReminderDelivery.POST_FAILED, local.dailyReminder.deliver(now, rome) { false })
        assertEquals(ReminderDelivery.SENT, local.dailyReminder.deliver(now, rome) { true })
        assertEquals(ReminderDelivery.ALREADY_SENT, local.dailyReminder.deliver(now, rome) { fail("Duplicate"); false })
    }

    private fun worker(run: suspend () -> ReminderDelivery?): DailyReminderWorker =
        TestListenableWorkerBuilder<DailyReminderWorker>(context).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: android.content.Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                DailyReminderWorker(appContext, workerParameters, run)
        }).build()

    @Test fun realCoroutineWorkerUsesFakeClockAndPermissionDenialLeavesDiaryAndDayAvailable() = runBlocking<Unit> {
        observe()
        var allowed = false
        var posts = 0
        val runner = ReminderRunner(local.dailyReminder, clock, { rome }, { allowed }, { posts++; true })
        assertEquals(ListenableWorker.Result.success(), worker(runner::run).doWork())
        assertEquals(0, posts)
        assertEquals("sentinella", local.diary.get("o")!!.notes)
        allowed = true
        clock.epochMillis = now.minusSeconds(1).toEpochMilli()
        assertEquals(ListenableWorker.Result.success(), worker(runner::run).doWork())
        assertEquals(0, posts)
        clock.epochMillis = now.toEpochMilli()
        assertEquals(ListenableWorker.Result.success(), worker(runner::run).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(runner::run).doWork())
        assertEquals(1, posts)
    }

    @Test fun coroutineWorkerRetriesPostAndStorageFailures() = runBlocking<Unit> {
        assertEquals(ListenableWorker.Result.retry(), worker { ReminderDelivery.POST_FAILED }.doWork())
        assertEquals(ListenableWorker.Result.retry(), worker { throw java.io.IOException("temporary") }.doWork())
    }

    @Test fun migrationFromF10PreservesDiaryPhotosSettingsWishlistAndDrafts() = runBlocking<Unit> {
        db.close()
        context.deleteDatabase(name)
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO taxa VALUES (?, ?, ?, 'Animalia', 'ACCEPTED', 'SPECIES', 'fixture', 's', 'test', ?, 'CC0', 'Faunavia', 'synthetic', 'v1')",
                arrayOf(f8bTaxon.id, f8bTaxon.scientificName, f8bTaxon.commonName, now.toString()))
            execSQL("INSERT INTO observations VALUES ('o', ?, ?, ?, 'Europe/Rome', NULL, NULL, 'legacy', ?, ?, 7, NULL, NULL)",
                arrayOf<Any>(f8bTaxon.id, now.toString(), now.epochSecond, now.toString(), now.toString()))
            execSQL("INSERT INTO observation_photos VALUES ('p', 'o', 'photos/legacy.jpg', ?, 100, 'image/jpeg', 80, 40, 1, 'thumbs/legacy.jpg')", arrayOf("a".repeat(64)))
            execSQL("INSERT INTO app_settings VALUES (1, 1, 1230, 'Europe/Rome')")
            execSQL("INSERT INTO wishlist VALUES (?, ?)", arrayOf(f8bTaxon.id, now.toString()))
            execSQL("INSERT INTO unidentified_drafts VALUES ('d', ?, 'Europe/Rome', NULL, NULL, 'draft', 1, NULL, NULL, ?, ?)",
                arrayOf(now.toString(), now.toString(), now.toString()))
            close()
        }
        helper.runMigrationsAndValidate(name, 9, true, FaunaviaDatabase.MIGRATION_8_9).close()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, clock)
        assertEquals(AppSettings(true, time, rome), local.settings.get())
        assertEquals("legacy", local.diary.get("o")!!.notes)
        assertEquals(7, local.diary.get("o")!!.quantity)
        assertEquals("thumbs/legacy.jpg", local.diary.photos("o").single().thumbnailPath)
        assertEquals("draft", local.unidentified.get("d")!!.input.notes)
        assertEquals(f8bTaxon.id, local.wishlist.list().single().taxonId)
        assertEquals(ReminderDelivery.SENT, local.dailyReminder.deliver(now, rome) { true })
    }
}
