package com.telerelay.domain.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bodies are real-world Turkish SMS (personal data removed). */
class AdSmsDetectorTest {

    @Test
    fun `bank campaign with SMSRET and MERSIS is an ad`() {
        assertTrue(
            AdSmsDetector.isAd(
                "QNB Kredi Karti'nizla Dyson magazalarinda pesin fiyatina 9 taksit firsati! " +
                    "Detay: https://qnb.mn/43241qc SMSRET->3639 MERSIS: 0388002333400576 B001",
            ),
        )
    }

    @Test
    fun `ret icin wording is an ad`() {
        assertTrue(
            AdSmsDetector.isAd(
                "Hemen 12 Aylik Pesin Odemede Indirimli Fiyat Firsati'na katil. " +
                    "Ret icin 3338'e RET TURKNET yaz. B043",
            ),
        )
    }

    @Test
    fun `iptal yaz wording is an ad`() {
        assertTrue(
            AdSmsDetector.isAd(
                "Bu akşam Jolly Joker sahnesinde! SMS almak istemiyorsanız JJK IPTAL yazıp " +
                    "4946'ya gönderebilirsiniz.",
            ),
        )
    }

    @Test
    fun `dotted capital I in SMS IPTAL is folded`() {
        assertTrue(AdSmsDetector.isAd("BUGÜNE ÖZEL %50 İNDİRİM. Bilgi;02165111009 SMS İPTAL jstsms.com B273"))
    }

    @Test
    fun `sms iptal icin wording is an ad`() {
        assertTrue(AdSmsDetector.isAd("Nakit avans firsati! SMS iptal icin H yazin 4944'e gonderin."))
    }

    @Test
    fun `betting spam with freebet is an ad`() {
        assertTrue(AdSmsDetector.isAd("DERBIYE OZEL 500TL FREEBET HEDIYE! https://dub.sh/MTDR B079"))
    }

    @Test
    fun `betting spam with bonus is an ad`() {
        assertTrue(AdSmsDetector.isAd("Yeni uyelere ozel 1500 Freespin! %100 Hosgeldin Bonusu! B064"))
    }

    @Test
    fun `betting signup with yatirim is an ad`() {
        assertTrue(
            AdSmsDetector.isAd(
                "PUSULABETE UYE OL ANINDA 500TL NAKIT KAZAN SART YOK ILK YATIRIMINA ÖZEL " +
                    "NE YATIRIRSAN 5000 TL BIZDEN B079",
            ),
        )
    }

    @Test
    fun `otp sms is not an ad`() {
        assertFalse(
            AdSmsDetector.isAd(
                "Dogrulama kodunuz : 259614  Bu mesaj e-Devlet Kapisi tarafindan " +
                    "21/09/2026 13:21:23 itibariyle gonderilmistir B002",
            ),
        )
    }

    @Test
    fun `3d secure sms is not an ad`() {
        assertFalse(
            AdSmsDetector.isAd(
                "01 no lu 3D secure sifreniz 121743 IYZICO ISTANBULKART ISTAN isleminizin " +
                    "306,00 TL tutarli sifresini paylasmayiniz. B002",
            ),
        )
    }

    @Test
    fun `incoming transfer notice is not an ad`() {
        assertFalse(
            AdSmsDetector.isAd(
                "Degerli musterimiz, hesabiniza 6.250 TL odeme gelmistir. Bakiye: 6.300 TL B016",
            ),
        )
    }

    @Test
    fun `fraud warning mentioning bahis is not an ad`() {
        assertFalse(
            AdSmsDetector.isAd(
                "Degerli musterimiz, hesaplarinizi hicbir kosulda ucuncu kisilere kullandirmayiniz. " +
                    "Hesabin bu sekilde kullandirilmasi; yasa disi bahis ve kumar, dolandiricilik " +
                    "gibi suclara aracilik edilmesine sebep olabilir.",
            ),
        )
    }

    @Test
    fun `cancelled package notice is not an ad`() {
        // "iptal" alone (without "icin"/"yaz") is service wording, not opt-out.
        assertFalse(
            AdSmsDetector.isAd(
                "1 aylik hediye paket kullanim sureniz dolmustur. Paketiniz iptal edilmis olup " +
                    "herhangi bir ucret yansitilmamistir. B002",
            ),
        )
    }

    @Test
    fun `voicemail notice is not an ad`() {
        assertFalse(
            AdSmsDetector.isAd(
                "17/09/2026 23:01 tarihinde size sesli mesaj bırakıldı. Mesajınızı 7530 u " +
                    "arayıp dinleyebilirsiniz. B002",
            ),
        )
    }

    @Test
    fun `personal message is not an ad`() {
        assertFalse(AdSmsDetector.isAd("baksanaaa"))
    }
}
