package com.telerelay.data.telegram

import com.telerelay.domain.port.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialises sends and enforces a minimum spacing between them.
 *
 * Telegram allows ~1 message/second per chat; without pacing, a burst of
 * forwarded messages would immediately trip flood limits (HTTP 429).
 */
@Singleton
class RateLimiter @Inject constructor(
    private val clock: Clock,
) {
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS
    private val mutex = Mutex()

    /** Half of Long.MIN_VALUE so "time since last send" can never overflow on the first call. */
    private var lastSendAt = Long.MIN_VALUE / 2

    suspend fun <T> withPermit(block: suspend () -> T): T = mutex.withLock {
        val sinceLast = clock.nowMillis() - lastSendAt
        if (sinceLast < minIntervalMillis) delay(minIntervalMillis - sinceLast)
        try {
            block()
        } finally {
            lastSendAt = clock.nowMillis()
        }
    }

    companion object {
        /** Slightly above Telegram's 1 msg/s per-chat budget. */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 1_100L
    }
}
