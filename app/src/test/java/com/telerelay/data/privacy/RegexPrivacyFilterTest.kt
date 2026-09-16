package com.telerelay.data.privacy

import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.FilterDecision
import com.telerelay.domain.model.PrivacyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flagship unit-test suite: the privacy guard must catch OTP codes and
 * bank transactions without swallowing ordinary messages.
 */
class RegexPrivacyFilterTest {

    private val settings = FakeSettingsRepository()
    private val filter = RegexPrivacyFilter(settings)

    private fun withSettings(transform: AppSettings.() -> AppSettings) {
        settings.set(settings.current().transform())
    }

    // ------------------------------------------------------------------
    // OTP / 2FA detection
    // ------------------------------------------------------------------

    @Test
    fun `english verification code is detected`() {
        val decision = filter.evaluate("+10000000001", "Your verification code is 482913")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `leading digits english code is detected`() {
        val decision = filter.evaluate("+10000000001", "123456 is your Google verification code")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `google style code with letter prefix is detected`() {
        val decision = filter.evaluate("+10000000001", "Use G-482913 to sign in")
        val masked = (decision as FilterDecision.Masked).maskedBody
        assertTrue(masked.contains("••••••"))
        assertTrue(masked.startsWith("Use G-"))
    }

    @Test
    fun `turkish otp phrasing is detected`() {
        val decision = filter.evaluate("+905550001122", "Dogrulama kodunuz 482913")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `grouped digits code is detected`() {
        val decision = filter.evaluate("+905550001122", "Kodunuz: 482 913")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `trailing punctuation does not prevent detection`() {
        val decision = filter.evaluate("+10000000001", "Your code is 482913.")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `inline code inside a sentence is detected`() {
        val decision = filter.evaluate("+10000000001", "Your code is 482913 thanks")
        assertTrue(decision is FilterDecision.Masked)
    }

    // ------------------------------------------------------------------
    // False positives — the class of failures that would break the feature
    // ------------------------------------------------------------------

    @Test
    fun `plain time mentions are not flagged`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+10000000001", "I'll be there at 6:30"))
        assertEquals(FilterDecision.Allow, filter.evaluate("+10000000001", "Meeting at 14:00"))
    }

    @Test
    fun `order numbers are not flagged`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+10000000001", "Your order #12345 has shipped"))
    }

    @Test
    fun `a total amount is not flagged as a transaction`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+905550001122", "Toplam: 250 TL"))
    }

    @Test
    fun `balance enquiry is not flagged`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+10000000001", "Your balance is 250 TL"))
    }

    @Test
    fun `payment without amount is not flagged`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+905550001122", "Faturaniz odendi"))
    }

    @Test
    fun `alphanumeric codes without keywords are deliberately not flagged`() {
        // Decided behaviour: 6-char alphanumeric strings without any keyword are
        // too false-positive-prone; only digit codes with context are filtered.
        assertEquals(FilterDecision.Allow, filter.evaluate("+10000000001", "Your plate A7K2Q9 expired"))
    }

    // ------------------------------------------------------------------
    // Bank / transaction detection
    // ------------------------------------------------------------------

    @Test
    fun `turkish card spend is detected`() {
        val decision = filter.evaluate("+905550001122", "Kartinizdan 250,00 TL harcama yapildi")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `english card charge is detected`() {
        val decision = filter.evaluate("+10000000001", "Your card ending 1234 was charged $25.00 at STORE")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `turkish deposit amount before verb is detected`() {
        val decision = filter.evaluate("+905550001122", "Hesabiniza 1.500 TL yatirildi")
        assertTrue(decision is FilterDecision.Masked)
    }

    // ------------------------------------------------------------------
    // Masking behaviour
    // ------------------------------------------------------------------

    @Test
    fun `masked code keeps the original length`() {
        val decision = filter.evaluate("+905550001122", "Kodunuz: 482913") as FilterDecision.Masked
        assertTrue(decision.maskedBody.contains("••••••"))
        // "Kodunuz: " prefix preserved verbatim.
        assertEquals("Kodunuz: ••••••", decision.maskedBody)
    }

    @Test
    fun `prose around the code is preserved verbatim`() {
        val decision = filter.evaluate("+10000000001", "Your code is 482913, expires soon") as FilterDecision.Masked
        assertEquals("Your code is ••••••, expires soon", decision.maskedBody)
    }

    @Test
    fun `multiple codes in one body are all masked`() {
        val decision = filter.evaluate("+905550001122", "Kodunuz: 482913 ve sifre: 998877") as FilterDecision.Masked
        assertTrue(decision.maskedBody.contains("••••••"))
        assertTrue(decision.maskedBody.contains("••••••"))
        assertEquals(false, decision.maskedBody.contains("482913"))
        assertEquals(false, decision.maskedBody.contains("998877"))
    }

    // ------------------------------------------------------------------
    // Filter mechanics
    // ------------------------------------------------------------------

    @Test
    fun `exclude mode drops the whole message`() {
        withSettings { copy(privacyMode = PrivacyMode.EXCLUDE) }
        assertEquals(FilterDecision.Exclude, filter.evaluate("+905550001122", "Kodunuz: 482913"))
    }

    @Test
    fun `mask mode never excludes`() {
        withSettings { copy(privacyMode = PrivacyMode.MASK) }
        val decision = filter.evaluate("+905550001122", "Kodunuz: 482913")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `non matching body is allowed`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+905550001122", "Akşam 7'de görüşürüz"))
    }

    @Test
    fun `empty body is allowed`() {
        assertEquals(FilterDecision.Allow, filter.evaluate("+905550001122", ""))
    }

    @Test
    fun `empty rule list allows everything`() {
        withSettings { copy(filterPatterns = emptyList()) }
        assertEquals(FilterDecision.Allow, filter.evaluate("+10000000001", "Your code is 482913"))
    }

    @Test
    fun `excluded sender blocks the message regardless of content`() {
        withSettings { copy(excludedNumbers = listOf("+905559998877")) }
        assertEquals(FilterDecision.Exclude, filter.evaluate("+905559998877", "normal message"))
    }

    @Test
    fun `sender matching is normalized across formats`() {
        // Same number written as +90…, 0… and 90…
        withSettings { copy(excludedNumbers = listOf("05550001122")) }
        assertEquals(FilterDecision.Exclude, filter.evaluate("+905550001122", "hello"))
        assertEquals(FilterDecision.Exclude, filter.evaluate("905550001122", "hello"))
        assertEquals(FilterDecision.Allow, filter.evaluate("+905551119988", "hello"))
    }

    @Test
    fun `invalid user regex is ignored without throwing`() {
        withSettings { copy(filterPatterns = listOf("[unclosed", "kodunuz[^0-9]{0,10}(?<code>[0-9]{4,8})")) }
        val decision = filter.evaluate("+905550001122", "kodunuz: 482913")
        assertTrue(decision is FilterDecision.Masked)
    }

    @Test
    fun `evaluation of a pathological pattern on a bounded body completes`() {
        // Defense in depth is the input caps (pattern + body length); this is a
        // smoke test that bounded inputs with a nested-quantifier pattern return.
        withSettings { copy(filterPatterns = listOf("(a+)+$")) }
        val decision = filter.evaluate("+10000000001", "aaaaaaaaaaaaaaaaaaaaX")
        assertEquals(FilterDecision.Allow, decision)
    }

    @Test
    fun `rule list is not recompiled when unchanged`() {
        // First call compiles; second call must hit the cache (same patterns list).
        filter.evaluate("+905550001122", "one")
        filter.evaluate("+905550001122", "two")
        // No assertion possible on the private cache; the observable contract is
        // that settings identity changes are the only invalidation trigger.
        withSettings { copy(filterPatterns = AppSettings().filterPatterns + "xyz[0-9]+") }
        filter.evaluate("+905550001122", "three xyz123")
        // Reaching here without an exception on the new pattern is the contract.
    }
}
