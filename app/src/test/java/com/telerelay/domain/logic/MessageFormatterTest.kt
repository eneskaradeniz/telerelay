package com.telerelay.domain.logic

import com.telerelay.domain.model.SimSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageFormatterTest {

    private val formatter = MessageFormatterImpl()

    @Test
    fun `sms is a compact sender line plus the body`() {
        val text = formatter.sms(
            sender = "+905550001122",
            body = "merhaba dünya",
            sim = null,
        )

        assertEquals(
            "📩 <b>+905550001122</b>\nmerhaba dünya",
            text,
        )
    }

    @Test
    fun `sim suffix is appended to the sender line when known`() {
        val text = formatter.sms(
            sender = "+905550001122",
            body = "hi",
            sim = SimSlot(slotIndex = 1),
        )

        assertEquals("📩 <b>+905550001122</b> · SIM2\nhi", text)
    }

    @Test
    fun `blank sender renders as Bilinmiyor`() {
        val text = formatter.sms(sender = "  ", body = "x", sim = null)

        assertEquals("📩 <b>Bilinmiyor</b>\nx", text)
    }

    @Test
    fun `body and sender are html escaped`() {
        val text = formatter.sms(sender = "A&B", body = "1<2>3 & done", sim = null)

        assertEquals("📩 <b>A&amp;B</b>\n1&lt;2&gt;3 &amp; done", text)
    }

    @Test
    fun `incoming call carries the caller`() {
        assertEquals("📞 <b>+905550001122</b> arıyor", formatter.incomingCall("+905550001122"))
        assertEquals("📞 <b>Ahmet</b> arıyor", formatter.incomingCall("Ahmet"))
    }

    @Test
    fun `call with unknown number says Bilinmiyor`() {
        assertEquals("📞 <b>Bilinmiyor</b> arıyor", formatter.incomingCall(null))
    }

    @Test
    fun `missed call is a single compact line`() {
        assertEquals("☎️ Cevapsız arama: <b>+905550001122</b>", formatter.missedCall("+905550001122"))
        assertEquals("☎️ Cevapsız arama: <b>Bilinmiyor</b>", formatter.missedCall(""))
    }

    @Test
    fun `call and missed callers are html escaped`() {
        // CallLog/broadcast numbers can carry odd characters; a crafted value
        // must not be able to inject markup.
        assertEquals("📞 <b>A&lt;B</b> arıyor", formatter.incomingCall("A<B"))
        assertEquals("☎️ Cevapsız arama: <b>A&amp;B</b>", formatter.missedCall("A&B"))
    }
}
