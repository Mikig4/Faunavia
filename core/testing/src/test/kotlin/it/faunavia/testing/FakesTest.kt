package it.faunavia.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class FakesTest {
    @Test
    fun fakeClockIsMutableAndDeterministic() {
        val clock = FakeClock(1_000)
        assertEquals(1_000, clock.nowEpochMillis())
        clock.epochMillis = 2_000
        assertEquals(2_000, clock.nowEpochMillis())
    }
}
