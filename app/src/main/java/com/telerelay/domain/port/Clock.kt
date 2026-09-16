package com.telerelay.domain.port

/** Time source; injectable so pure-Kotlin logic can be tested deterministically. */
fun interface Clock {
    fun nowMillis(): Long
}
