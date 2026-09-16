package com.telerelay.domain.port

import com.telerelay.domain.model.SimSlot

/** Renders the Telegram message bodies in the app's fixed visual format. */
interface MessageFormatter {
    fun sms(sender: String, body: String, receivedAtMillis: Long, sim: SimSlot?): String
    fun incomingCall(number: String?, atMillis: Long, sim: SimSlot?): String
    fun missedCall(number: String?): String
    fun testMessage(): String
}
