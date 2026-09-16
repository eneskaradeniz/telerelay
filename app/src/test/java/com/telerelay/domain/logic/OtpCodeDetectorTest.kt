package com.telerelay.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpCodeDetectorTest {

    @Test
    fun `finds the code in a real verification sms`() {
        val body = "Dogrulama kodunuz : 814067  Bu mesaj e-Devlet Kapisi " +
            "tarafindan 16/09/2026 16:06:39 itibariyle gonderilmistir B002"

        assertEquals("814067", OtpCodeDetector.find(body))
    }

    @Test
    fun `leftmost code wins`() {
        assertEquals("482913", OtpCodeDetector.find("Kod: 482913 (10 dk gecerli)"))
    }

    @Test
    fun `date fragments are not picked up`() {
        assertNull(OtpCodeDetector.find("16/09/2026 tarihinde isleminiz gerceklesti"))
    }

    @Test
    fun `times are not picked up`() {
        assertNull(OtpCodeDetector.find("16:06:39 itibariyle"))
    }

    @Test
    fun `too short or too long digit runs are ignored`() {
        assertNull(OtpCodeDetector.find("Kod: 123"))
        assertNull(OtpCodeDetector.find("Kod: 12345678901"))
        assertNull(OtpCodeDetector.find("Ref: B002"))
    }

    @Test
    fun `no code anywhere returns null`() {
        assertNull(OtpCodeDetector.find("Faturaniz hazir"))
        assertNull(OtpCodeDetector.find(""))
    }
}
