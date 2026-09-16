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

    @Test
    fun `marketing sms without otp wording gets no button`() {
        val body = "Giyim alisverislerinizi QNB Kredi Karti'yla yapin 4000 TL'ye varan " +
            "ParaPuan kazanin! 30 Eylul'e kadar okula donus kampanyasi kapsaminda giyim, " +
            "egitim ve kirtasiye sektorlerinde her 4000 TL ve uzeri harcamaniza 200 TL, " +
            "mobil odemeye ek 200 TL, toplamda 4000 TL ParaPuan kazanin! Katilim icin OKUL " +
            "yazip cevaplayin. ParaPuan son kullanim: 31 Ekim 2026 https://qnb.mn/43167qc " +
            "SMSRET->3639 MERSIS:0388002333400576 B001"

        assertNull(OtpCodeDetector.find(body))
    }

    @Test
    fun `bare amounts without otp wording get no button`() {
        assertNull(OtpCodeDetector.find("4000 TL'ye varan ParaPuan kazanin"))
        assertNull(OtpCodeDetector.find("Bakiyeniz 123456 TL"))
    }

    @Test
    fun `turkish verification wording is recognised`() {
        assertEquals("482913", OtpCodeDetector.find("Güvenlik doğrulama kodu: 482913"))
        assertEquals("555122", OtpCodeDetector.find("Islem onay sifreniz 555122"))
    }

    @Test
    fun `english verification wording is recognised`() {
        assertEquals("482913", OtpCodeDetector.find("Your verification code is 482913"))
        assertEquals("998877", OtpCodeDetector.find("OTP: 998877"))
    }

    @Test
    fun `inflected sifreniz keyword is recognised`() {
        // Real-world body (makromusic): the keyword inflects ("sifre" -> "sifreniz").
        assertEquals("240874", OtpCodeDetector.find("makromusic tek kullanimlik sifreniz 240874\nB186"))
    }

    @Test
    fun `real-world verification bodies are all detected`() {
        val bodies = mapOf(
            "Tinder kodun: 625249 B043" to "625249",
            "461751 dogrulama kodu ile Tikla Gelsin hesabiniza giris yapabilirsiniz. " +
                "Bu SMS'i siz talep etmediyseniz lütfen göz ardi ediniz. pUpAYaJAqj7 B002" to "461751",
            "IDEFIX PAZARYERI isyerinden 08.08.2026 00:42 tarihli 18394,40TL harcamaniz " +
                "icin sifre:606414 Referans:MNWJONRO.Lutfen sifreyi kimseyle paylasmayin B001" to "606414",
            "02 no lu 3D secure sifreniz 339767 IYZICO ISTANBULKART ISTAN isleminizin " +
                "105,00 TL tutarli sifresini paylasmayiniz. B002" to "339767",
            "Dogrulama kodunuz: 9255049. Bu kodu kimseyle paylasmayin; calisanlarimiz " +
                "asla bu kodu sormazlar.\nTWVerify ile gonderildi. B043" to "9255049",
        )
        bodies.forEach { (body, expected) ->
            assertEquals(expected, OtpCodeDetector.find(body))
        }
    }
}
