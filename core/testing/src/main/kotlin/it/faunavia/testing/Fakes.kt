package it.faunavia.testing

import it.faunavia.domain.AppClock

class FakeClock(
    var epochMillis: Long,
) : AppClock {
    override fun nowEpochMillis(): Long = epochMillis
}
