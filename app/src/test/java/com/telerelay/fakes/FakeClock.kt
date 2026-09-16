package com.telerelay.fakes

import com.telerelay.domain.port.Clock

/** Manually advanced clock for deterministic time-based logic in tests. */
class FakeClock(startMillis: Long = 0L) : Clock {

    private var now: Long = startMillis

    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }

    fun set(millis: Long) {
        now = millis
    }
}
