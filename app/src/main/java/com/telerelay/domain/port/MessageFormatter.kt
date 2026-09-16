package com.telerelay.domain.port

import com.telerelay.domain.model.SimSlot

/**
 * Renders the Telegram message bodies in the app's fixed visual format
 * (HTML parse mode). The wording is intentionally constant (not localized) so
 * relayed messages look the same regardless of the phone's UI language.
 */
interface MessageFormatter {

    /** Compact SMS: sender line (contact name when known) + body directly beneath. */
    fun sms(sender: String?, body: String, sim: SimSlot?): String

    /** No SIM marker on calls — the public API carries no subscription on ringing events. */
    fun incomingCall(caller: String?): String

    fun missedCall(caller: String?): String

    fun testMessage(): String
}
