package com.telerelay.domain.logic

import com.telerelay.domain.model.SimSlot
import com.telerelay.domain.port.MessageFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Renders the fixed message formats forwarded to Telegram. The wording is
 * intentionally constant (not localized) so relayed messages look the same
 * regardless of the phone's UI language.
 */
class MessageFormatterImpl @Inject constructor(
    private val zone: ZoneId,
) : MessageFormatter {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun sms(sender: String, body: String, receivedAtMillis: Long, sim: SimSlot?): String = buildString {
        append("📩 Yeni SMS\n")
        append("Kimden: ").append(sender.ifBlank { "Bilinmiyor" }).append('\n')
        append("Zaman: ").append(time(receivedAtMillis)).append('\n')
        appendSim(sim)
        append("Mesaj: ").append(body)
    }

    override fun incomingCall(number: String?, atMillis: Long, sim: SimSlot?): String = buildString {
        append("📞 Gelen Arama\n")
        append("Arayan: ").append(number?.ifBlank { null } ?: "Bilinmiyor").append('\n')
        append("Zaman: ").append(time(atMillis)).append('\n')
        appendSim(sim)
        deleteCharAt(length - 1)
    }

    override fun missedCall(number: String?): String =
        "☎️ Cevapsız arama: ${number?.ifBlank { null } ?: "Bilinmiyor"}"

    override fun testMessage(): String = "✅ TeleRelay test mesajı — bağlantı çalışıyor."

    private fun time(atMillis: Long): String =
        timeFormat.format(Instant.ofEpochMilli(atMillis).atZone(zone))

    private fun StringBuilder.appendSim(sim: SimSlot?) {
        if (sim != null) {
            append("SIM: ").append(sim.label)
            if (!sim.carrierName.isNullOrBlank()) append(" (").append(sim.carrierName).append(')')
            append('\n')
        }
    }
}
