package com.telerelay.domain.logic

import com.telerelay.domain.model.SimSlot
import com.telerelay.domain.port.MessageFormatter
import javax.inject.Inject

/**
 * Renders the fixed message formats forwarded to Telegram (HTML parse mode,
 * see the gateway). Deliberately compact — a forwarded message reads like the
 * SMS itself: sender line, then the body. Telegram already shows the receive
 * time, so no time line; the SIM marker appears only when the message's SIM is
 * actually known (multi-SIM devices).
 *
 * The wording is intentionally constant (not localized) so relayed messages
 * look the same regardless of the phone's UI language.
 */
class MessageFormatterImpl @Inject constructor() : MessageFormatter {

    override fun sms(sender: String?, body: String, sim: SimSlot?): String = buildString {
        append("📩 <b>").append(escape(display(sender))).append("</b>")
        append(simSuffix(sim))
        append('\n').append(escape(body))
    }

    override fun incomingCall(caller: String?): String =
        "📞 <b>${escape(display(caller))}</b> arıyor"

    override fun missedCall(caller: String?): String =
        "☎️ Cevapsız arama: <b>${escape(display(caller))}</b>"

    override fun testMessage(): String = "✅ TeleRelay test mesajı — bağlantı çalışıyor."

    private fun display(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "Bilinmiyor"

    private fun simSuffix(sim: SimSlot?): String = sim?.let { " · ${it.label}" } ?: ""

    /** Telegram HTML parse mode only treats &, < and > specially in message text. */
    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
