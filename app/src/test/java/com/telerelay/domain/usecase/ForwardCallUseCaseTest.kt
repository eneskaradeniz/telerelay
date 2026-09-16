package com.telerelay.domain.usecase

import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.CallState
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.fakes.FakeCallerNumberResolver
import com.telerelay.fakes.FakeClock
import com.telerelay.fakes.FakeContactNameResolver
import com.telerelay.fakes.FakeMessageFormatter
import com.telerelay.fakes.FakeMessageSender
import com.telerelay.fakes.FakeSettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForwardCallUseCaseTest {

    private val clock = FakeClock()
    private val settings = FakeSettingsRepository(
        AppSettings(botToken = "123:SECRET", chatId = "42"),
    )
    private val formatter = FakeMessageFormatter()
    private val sender = FakeMessageSender()
    private val resolver = FakeCallerNumberResolver()
    private val contacts = FakeContactNameResolver()
    private val useCase = ForwardCallUseCase(
        settings = settings,
        formatter = formatter,
        sender = sender,
        resolver = resolver,
        contacts = contacts,
        clock = clock,
    )

    @Before
    fun resetRecordings() {
        formatter.reset()
        sender.sent.clear()
    }

    private fun ringEvent(at: Long) = CallStateEvent(CallState.RINGING, number = null, atMillis = at)
    private fun idleEvent(at: Long) = CallStateEvent(CallState.IDLE, number = null, atMillis = at)

    @Test
    fun `broadcast number landing during the grace window names the caller`() = runTest {
        contacts.directory["+905550001122"] = "Eyüp"
        val job = launch { useCase.onEvent(ringEvent(at = 1_000)) }

        advanceTimeBy(300) // mid-grace: the 750 ms window is still open
        clock.set(1_300)
        useCase.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 1_300)
        runCurrent()
        advanceTimeBy(600)
        job.join()

        assertEquals("Eyüp", formatter.lastCallCaller)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `missed notification keeps the number the incoming delivered`() = runTest {
        val job = launch { useCase.onEvent(ringEvent(at = 1_000)) }
        advanceTimeBy(300)
        clock.set(1_300)
        useCase.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 1_300)
        advanceTimeBy(600)
        job.join()
        assertEquals("+905550001122", formatter.lastCallCaller)

        clock.set(25_000)
        useCase.onEvent(idleEvent(at = 25_000))

        assertEquals("+905550001122", formatter.lastMissedCaller)
    }

    @Test
    fun `disabled incoming toggle suppresses only the incoming message`() = runTest {
        settings.set(
            AppSettings(
                botToken = "123:SECRET",
                chatId = "42",
                callNotificationEnabled = false,
                missedCallNotificationEnabled = true,
            ),
        )

        useCase.onEvent(CallStateEvent(CallState.RINGING, number = "+905550001122", atMillis = 1_000))
        assertEquals(0, sender.sent.size)

        clock.set(25_000)
        useCase.onEvent(idleEvent(at = 25_000))

        assertEquals(1, sender.sent.size)
        assertEquals("+905550001122", formatter.lastMissedCaller)
    }

    @Test
    fun `unconfigured credentials send nothing`() = runTest {
        settings.set(AppSettings())

        useCase.onEvent(CallStateEvent(CallState.RINGING, number = "+905550001122", atMillis = 1_000))
        clock.set(25_000)
        useCase.onEvent(idleEvent(at = 25_000))

        assertTrue(sender.sent.isEmpty())
    }
}
