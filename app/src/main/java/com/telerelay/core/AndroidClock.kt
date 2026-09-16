package com.telerelay.core

import com.telerelay.domain.port.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Wall-clock implementation of [Clock]. */
@Singleton
class AndroidClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
