package com.telerelay.domain.logic

import com.telerelay.domain.model.SimSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class MessageFormatterTest {

    private val formatter = MessageFormatterImpl(zone = ZoneId.of("UTC"))

    @Test
    fun `sms body matches the fixed format exactly`() {
        val text = formatter.sms(
            sender = "+905550001122",
            body = "merhaba dünya",
            receivedAtMillis = 78_000_000, // 21:40:00 UTC
            sim = null,
        )

        assertEquals(
            """
            📩 Yeni SMS
            Kimden: +905550001122
            Zaman: 21:40:00
            Mesaj: merhaba dünya
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `sim suffix is inserted before the message line when known`() {
        val text = formatter.sms(
            sender = "+905550001122",
            body = "hi",
            receivedAtMillis = 0,
            sim = SimSlot(slotIndex = 0, carrierName = "Vodafone"),
        )

        assertEquals(
            """
            📩 Yeni SMS
            Kimden: +905550001122
            Zaman: 00:00:00
            SIM: SIM1 (Vodafone)
            Mesaj: hi
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `sim line carries no carrier when the name is blank`() {
        val text = formatter.incomingCall(
            number = "+905550001122",
            atMillis = 0,
            sim = SimSlot(slotIndex = 1, carrierName = "  "),
        )

        assertEquals(
            """
            📞 Gelen Arama
            Arayan: +905550001122
            Zaman: 00:00:00
            SIM: SIM2
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `call with unknown number says Bilinmiyor`() {
        val text = formatter.incomingCall(number = null, atMillis = 3_600_000, sim = null)

        assertEquals(
            """
            📞 Gelen Arama
            Arayan: Bilinmiyor
            Zaman: 01:00:00
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `missed call is a single compact line`() {
        assertEquals("☎️ Cevapsız arama: +905550001122", formatter.missedCall("+905550001122"))
        assertEquals("☎️ Cevapsız arama: Bilinmiyor", formatter.missedCall(""))
    }

    @Test
    fun `time is zero padded`() {
        val text = formatter.sms("+905550001122", "x", receivedAtMillis = 1_000, sim = null)
        assert(text.contains("Zaman: 00:00:01"))
    }
}
