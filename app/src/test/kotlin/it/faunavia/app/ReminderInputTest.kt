package it.faunavia.app

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ReminderInputTest {
    @Test fun acceptsOnlyMinutePrecisionAndValid24HourTimes() {
        assertEquals(LocalTime.MIDNIGHT, parseReminderTime("00:00"))
        assertEquals(LocalTime.of(23, 59), parseReminderTime("23:59"))
        for (value in listOf("24:00", "12:60", "8:00", "20:30:00", "", " 20:30")) assertNull(parseReminderTime(value))
    }
}
