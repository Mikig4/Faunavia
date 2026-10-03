package it.faunavia.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.faunavia.domain.AppClock
import it.faunavia.domain.AppSettings
import it.faunavia.domain.DailyReminderPolicy
import it.faunavia.domain.DailyReminderRepository
import it.faunavia.domain.DailySummary
import it.faunavia.domain.ReminderDelivery
import it.faunavia.domain.SettingsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class SummaryLink(val date: LocalDate, val zoneId: ZoneId) {
    companion object {
        private const val DATE = "it.faunavia.summary.DATE"
        private const val ZONE = "it.faunavia.summary.ZONE"
        fun read(intent: Intent): SummaryLink? = runCatching {
            SummaryLink(LocalDate.parse(intent.getStringExtra(DATE)), ZoneId.of(intent.getStringExtra(ZONE)))
        }.getOrNull()
        fun clear(intent: Intent) { intent.removeExtra(DATE); intent.removeExtra(ZONE) }
    }

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction("it.faunavia.summary.$date")
        .putExtra(DATE, date.toString()).putExtra(ZONE, zoneId.id)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

internal class DailySummaryNotifications(private val context: Context) {
    companion object {
        const val CHANNEL = "daily-summary"
        const val NOTIFICATION_ID = 11
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Riepilogo degli avvistamenti",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Un riepilogo locale della giornata, all’orario indicativo scelto."
        })
    }

    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun cancel() {
        manager.activeNotifications.filter { it.id == NOTIFICATION_ID }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun post(summary: DailySummary): Boolean {
        ensureChannel()
        if (!allowed()) return false
        val tap = PendingIntent.getActivity(context, 11, SummaryLink(summary.date, summary.zoneId).intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = if (summary.observationCount == 1) "Hai registrato 1 avvistamento oggi"
            else "Hai registrato ${summary.observationCount} avvistamenti oggi"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Gli avvistamenti di oggi")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText("$message. Apri specie e foto della giornata."))
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        // A date-specific tag alerts on a new day, but a crash retry replaces the same day's card silently.
        return try { manager.notify(summary.date.toString(), NOTIFICATION_ID, notification); true }
        catch (_: SecurityException) { false }
    }
}

internal class ReminderRunner(
    private val repository: DailyReminderRepository,
    private val clock: AppClock,
    private val zone: () -> ZoneId,
    private val allowed: () -> Boolean,
    private val post: (DailySummary) -> Boolean,
) {
    suspend fun run(): ReminderDelivery? = if (!allowed()) null
        else repository.deliver(Instant.ofEpochMilli(clock.nowEpochMillis()), zone(), post)
}

/** The public two-argument constructor is used by WorkManager's reflection factory. */
class DailyReminderWorker internal constructor(context: Context, parameters: WorkerParameters,
    private val runReminder: suspend () -> ReminderDelivery?) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters,
        { (context.applicationContext as FaunaviaApplication).reminderRunner.run() })

    override suspend fun doWork(): Result = try {
        if (runReminder() == ReminderDelivery.POST_FAILED) Result.retry() else Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}

internal class ReminderScheduler(
    private val workManager: WorkManager,
    private val now: () -> Instant = { Instant.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    companion object { const val WORK_NAME = "faunavia-daily-summary" }

    suspend fun reconcile(settings: AppSettings, replace: Boolean = false) = withContext(Dispatchers.IO) {
        if (!settings.reminderEnabled) {
            workManager.cancelUniqueWork(WORK_NAME).result.get()
        } else {
            // A short periodic check avoids 24-hour drift across DST. It reads no diary before the preferred time.
            val delay = DailyReminderPolicy.initialDelay(now(), zone(), settings.reminderTime)
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request).result.get()
        }
        Unit
    }
}

internal class ReminderPreferences(
    private val repository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: DailySummaryNotifications,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()

    suspend fun <T> withBackup(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun reconcile(force: Boolean = false): AppSettings = withContext(NonCancellable) { mutex.withLock {
        notifications.ensureChannel()
        val old = repository.get()
        val current = old.copy(zoneId = zone(), reminderEnabled = old.reminderEnabled && notifications.allowed())
        if (old != current) repository.save(current)
        scheduler.reconcile(current, replace = force || old.zoneId != current.zoneId)
        if (!current.reminderEnabled) notifications.cancel()
        current
    } }

    // Finish persistence and scheduling even when the settings composition disappears mid-save.
    suspend fun save(enabled: Boolean, time: LocalTime): AppSettings = withContext(NonCancellable) { mutex.withLock {
        notifications.ensureChannel()
        val old = repository.get()
        val current = old.copy(reminderEnabled = enabled && notifications.allowed(), reminderTime = time, zoneId = zone())
        repository.save(current)
        scheduler.reconcile(current, replace = old != current)
        if (!current.reminderEnabled) notifications.cancel()
        current
    } }
}

/** WorkManager restores jobs at boot. Clock/timezone broadcasts realign the first eligible check. */
class ReminderTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as FaunaviaApplication).reminderPreferences.reconcile(force = true) }
            catch (failure: Exception) { reminderRecoveryWarning(failure, "Cannot realign reminder; next app opening retries.") }
            finally { pending.finish() }
        }
    }
}

// MapLibre brings a Timber lint rule through its runtime dependency; Timber is not an app API.
@SuppressLint("LogNotTimber")
internal fun reminderRecoveryWarning(failure: Exception, message: String) {
    if (failure is CancellationException) throw failure
    Log.w("FaunaviaReminder", message, failure)
}
