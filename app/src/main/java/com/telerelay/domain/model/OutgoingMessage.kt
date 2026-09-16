package com.telerelay.domain.model

/**
 * A formatted message on its way to Telegram, carried as one unit through the
 * immediate send and the encrypted retry queue.
 *
 * @param text final message body (already HTML-formatted by the [com.telerelay.domain.port.MessageFormatter]).
 * @param copyText when non-null, the message carries Telegram's native one-tap
 *   copy button with this value — used for detected OTP codes.
 */
data class OutgoingMessage(
    val text: String,
    val copyText: String? = null,
)
