package com.telerelay.domain.port

/**
 * Fire-and-forget delivery used by use cases: attempts to send immediately and,
 * when the gateway says the failure is transient, hands the message to the retry
 * mechanism instead of losing it.
 */
interface MessageSender {
    suspend fun sendOrEnqueue(text: String)
}
