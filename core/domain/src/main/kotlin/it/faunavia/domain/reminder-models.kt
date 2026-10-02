package it.faunavia.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DailySummary(val date: LocalDate, val zoneId: ZoneId, val observationCount: Int) {
    init { require(observationCount > 0) }
}

enum class ReminderDelivery { DISABLED, BEFORE_TIME, EMPTY, ALREADY_SENT, SENT, POST_FAILED }

/** Evaluate the current local day; a delayed job never sends yesterday's summary. */
object DailyReminderPolicy {
    fun eligibleDate(now: Instant, zone: ZoneId, preferredTime: LocalTime): LocalDate? {
        val local = now.atZone(zone)
        return local.toLocalDate().takeIf { local.toLocalTime() >= preferredTime }
    }

    fun initialDelay(now: Instant, zone: ZoneId, preferredTime: LocalTime): Duration {
        if (eligibleDate(now, zone, preferredTime) != null) return Duration.ZERO
        val target = now.atZone(zone).toLocalDate().atTime(preferredTime).atZone(zone).toInstant()
        return Duration.between(now, target).coerceAtLeast(Duration.ZERO)
    }
}

interface DailyReminderRepository {
    /** Serializes the local snapshot, posting and durable daily marker. No network is involved. */
    suspend fun deliver(now: Instant, zone: ZoneId, post: (DailySummary) -> Boolean): ReminderDelivery
}
