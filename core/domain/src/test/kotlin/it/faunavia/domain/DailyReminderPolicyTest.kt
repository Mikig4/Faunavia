package it.faunavia.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DailyReminderPolicyTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val time = LocalTime.of(20, 30)

    @Test fun preferredMinuteStartsTheWindowAndMidnightClosesIt() {
        assertNull(DailyReminderPolicy.eligibleDate(Instant.parse("2026-10-02T18:29:59Z"), rome, time))
        assertEquals(LocalDate.of(2026, 10, 2), DailyReminderPolicy.eligibleDate(Instant.parse("2026-10-02T18:30:00Z"), rome, time))
        assertEquals(LocalDate.of(2026, 10, 2), DailyReminderPolicy.eligibleDate(Instant.parse("2026-10-02T21:59:59Z"), rome, time))
        assertNull(DailyReminderPolicy.eligibleDate(Instant.parse("2026-10-02T22:00:00Z"), rome, time))
    }

    @Test fun deviceZoneDeterminesTheDayAndInitialDelay() {
        val now = Instant.parse("2026-10-02T18:00:00Z")
        assertEquals(Duration.ofMinutes(30), DailyReminderPolicy.initialDelay(now, rome, time))
        assertEquals(Duration.ofHours(6).plusMinutes(30), DailyReminderPolicy.initialDelay(now, ZoneId.of("America/New_York"), time))
        assertEquals(Duration.ZERO, DailyReminderPolicy.initialDelay(now, ZoneId.of("Asia/Tokyo"), LocalTime.of(2, 0)))
    }

    @Test fun daylightSavingGapResolvesForwardAndRepeatedHourStaysEligible() {
        val early = LocalTime.of(2, 30)
        assertEquals(Duration.ofHours(1), DailyReminderPolicy.initialDelay(Instant.parse("2026-03-29T00:30:00Z"), rome, early))
        assertEquals(LocalDate.of(2026, 3, 29), DailyReminderPolicy.eligibleDate(Instant.parse("2026-03-29T01:30:00Z"), rome, early))
        for (instant in listOf("2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z")) {
            assertEquals(LocalDate.of(2026, 10, 25), DailyReminderPolicy.eligibleDate(Instant.parse(instant), rome, early))
        }
    }
}
