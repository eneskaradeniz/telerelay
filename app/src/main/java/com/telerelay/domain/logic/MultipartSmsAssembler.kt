package com.telerelay.domain.logic

import com.telerelay.domain.model.IncomingSms
import com.telerelay.domain.port.Clock

/**
 * Reassembles long SMS messages.
 *
 * Long texts are transmitted as several concatenated segments. Without UDH
 * access (not exposed to non-default SMS apps) there is no reliable way to know
 * the segment count, so segments are buffered per sender for a short quiet
 * window and flushed when no further segment arrives.
 *
 * Fast path: when a single broadcast already carries several PDUs, the platform
 * delivered the whole message at once and it is emitted immediately with no delay.
 *
 * Pure Kotlin — no Android dependencies, driven by an injected [Clock] in tests.
 */
class MultipartSmsAssembler(
    private val clock: Clock,
    /** How long to wait for the next segment before flushing a sender's buffer. */
    val quietWindowMillis: Long = DEFAULT_QUIET_WINDOW_MILLIS,
    /** Safety cap so a runaway buffer can never grow unbounded. */
    private val maxSegments: Int = MAX_SEGMENTS,
) {
    private class Buffer(val parts: MutableList<String>) {
        var firstArrival: Long = 0
        var lastArrival: Long = 0
    }

    private val buffers = LinkedHashMap<String, Buffer>()

    /**
     * Feeds one broadcast into the assembler.
     * @return the complete message if this call completed one, null while buffering.
     */
    fun offer(incoming: IncomingSms): IncomingSms? {
        if (incoming.body.isEmpty()) return null

        if (incoming.segmentCount > 1) {
            // Whole message arrived in one broadcast; drop any stale partial buffer.
            buffers.remove(incoming.sender)
            return incoming
        }

        val buffer = buffers[incoming.sender]
        if (buffer == null) {
            buffers[incoming.sender] = Buffer(mutableListOf(incoming.body)).apply {
                firstArrival = incoming.receivedAtMillis
                lastArrival = incoming.receivedAtMillis
            }
            return null
        }

        buffer.parts.add(incoming.body)
        buffer.lastArrival = clock.nowMillis()
        return if (buffer.parts.size >= maxSegments) drain(incoming.sender) else null
    }

    /**
     * Flushes every buffer whose quiet window has elapsed. Called opportunistically:
     * before each new message and from a scheduled timer in the use case.
     */
    fun emitExpired(): List<IncomingSms> {
        val now = clock.nowMillis()
        val expired = buffers.entries
            .filter { now - it.value.lastArrival >= quietWindowMillis }
            .map { it.key }
        return expired.mapNotNull(::drain)
    }

    private fun drain(sender: String): IncomingSms? {
        val buffer = buffers.remove(sender) ?: return null
        val joined = buffer.parts.joinToString(separator = "")
        return IncomingSms(
            sender = sender,
            body = joined,
            receivedAtMillis = buffer.firstArrival,
            segmentCount = buffer.parts.size,
        )
    }

    companion object {
        /**
         * Most concatenation segments arrive within a couple of seconds of each
         * other; 5 s keeps the added latency negligible while reliably merging them.
         */
        const val DEFAULT_QUIET_WINDOW_MILLIS = 5_000L
        const val MAX_SEGMENTS = 10
    }
}
