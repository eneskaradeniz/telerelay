package com.telerelay.domain.model

/**
 * An SMS captured from the [android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION]
 * broadcast, before any assembly, filtering or formatting.
 *
 * @param sender        originating address (phone number) as reported by the PDU.
 * @param body          message text; for a multi-PDU broadcast the segments are already joined.
 * @param receivedAtMillis epoch millis at which the broadcast was delivered.
 * @param segmentCount  number of PDUs in the delivering broadcast. Concatenated messages
 *                      usually arrive as several single-PDU broadcasts; a broadcast that
 *                      already carries several PDUs is treated as a complete message.
 */
data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val segmentCount: Int = 1,
)
