package com.telerelay.domain.usecase

import com.telerelay.domain.logic.MultipartSmsAssembler
import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.IncomingSms
import com.telerelay.domain.model.SimSlot
import com.telerelay.fakes.FakeClock
import com.telerelay.fakes.FakeContactNameResolver
import com.telerelay.fakes.FakeMessageFormatter
import com.telerelay.fakes.FakeMessageSender
import com.telerelay.fakes.FakeSettingsRepository
import com.telerelay.fakes.FakeSimInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForwardSmsUseCaseTest {

    private val clock = FakeClock()
    private val settings = FakeSettingsRepository(
        AppSettings(botToken = "123:SECRET", chatId = "42"),
    )
    private val contacts = FakeContactNameResolver()
    private val formatter = FakeMessageFormatter()
    private val sender = FakeMessageSender()
    private val simInfo = FakeSimInfoProvider(SimSlot(slotIndex = 0))

    private fun useCase(scope: CoroutineScope) = ForwardSmsUseCase(
        assembler = MultipartSmsAssembler(clock),
        settings = settings,
        contacts = contacts,
        formatter = formatter,
        sender = sender,
        simInfo = simInfo,
        clock = clock,
        scope = scope,
    )

    private val useCase = useCase(CoroutineScope(Dispatchers.Unconfined))

    @Before
    fun resetRecordings() {
        formatter.reset()
        sender.sent.clear()
    }

    /**
     * Single-PDU broadcasts enter the 5 s merging window (deliver returns via
     * the scheduled flush), so synchronous-path tests use a multi-PDU
     * broadcast — the assembler's fast path.
     */
    private fun deliveredSms(
        sender: String = "2273",
        body: String = "x",
        subscriptionId: Int? = null,
    ) = IncomingSms(
        sender = sender,
        body = body,
        receivedAtMillis = 1_000,
        segmentCount = 2,
        subscriptionId = subscriptionId,
    )

    @Test
    fun `non-contact short-code sender keeps the raw sender id`() = runBlocking {
        // "2273" is a real-world service short code: not in contacts, never
        // "Bilinmiyor" — an SMS always has a sender.
        useCase(deliveredSms(body = "Kod: 814067"))

        assertEquals("2273", formatter.lastSmsSender)
        assertTrue(sender.sent.isNotEmpty())
    }

    @Test
    fun `saved contact name replaces the sender`() = runBlocking {
        contacts.directory["+905550001122"] = "Eyüp"

        useCase(deliveredSms(sender = "+905550001122", body = "selam"))

        assertEquals("Eyüp", formatter.lastSmsSender)
    }

    @Test
    fun `otp code rides the outgoing message as copy text`() = runBlocking {
        useCase(deliveredSms(body = "Dogrulama kodunuz : 814067"))

        assertEquals("814067", sender.sent.single().copyText)
    }

    @Test
    fun `body without a code sends no copy button`() = runBlocking {
        useCase(deliveredSms(body = "Faturaniz hazir"))

        assertNull(sender.sent.single().copyText)
    }

    @Test
    fun `subscription id and sim marker reach the formatter`() = runBlocking {
        useCase(deliveredSms(subscriptionId = 5))

        assertEquals(5, simInfo.lastSubscriptionId)
        assertEquals(SimSlot(slotIndex = 0), formatter.lastSmsSim)
    }

    @Test
    fun `forwarding disabled sends nothing`() = runBlocking {
        settings.set(AppSettings(botToken = "123:SECRET", chatId = "42", smsForwardingEnabled = false))

        useCase(deliveredSms())

        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `unconfigured credentials send nothing`() = runBlocking {
        settings.set(AppSettings())

        useCase(deliveredSms())

        assertTrue(sender.sent.isEmpty())
    }

    // --- buffered delivery: the 5 s merging-window path ----------------------

    @Test
    fun `flushed single-part message carries sender fallback and copy text`() = runTest {
        val buffered = useCase(backgroundScope)

        buffered(IncomingSms(sender = "2273", body = "Kod: 814067", receivedAtMillis = 1_000))
        assertEquals(0, sender.sent.size) // waiting out the quiet window

        // The flush reads the injected Clock, which must advance with virtual time.
        clock.set(7_000)
        advanceTimeBy(6_000) // quiet window (5 s) + flush grace

        assertEquals("2273", formatter.lastSmsSender)
        assertEquals("814067", sender.sent.single().copyText)
        assertEquals(SimSlot(slotIndex = 0), formatter.lastSmsSim)
    }

    @Test
    fun `toggling forwarding off while a segment is buffered cancels the delivery`() = runTest {
        val buffered = useCase(backgroundScope)

        buffered(IncomingSms(sender = "2273", body = "Kod: 814067", receivedAtMillis = 1_000))
        settings.set(AppSettings(botToken = "123:SECRET", chatId = "42", smsForwardingEnabled = false))

        clock.set(7_000) // the flush genuinely runs; the settings re-check suppresses it
        advanceTimeBy(6_000)

        assertTrue(sender.sent.isEmpty())
    }
}
