package com.telerelay.domain.logic

import com.telerelay.domain.model.IncomingSms
import com.telerelay.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultipartSmsAssemblerTest {

    private val clock = FakeClock()
    private val assembler = MultipartSmsAssembler(clock)

    private fun sms(sender: String = "+905550001122", body: String, at: Long = clock.nowMillis(), segments: Int = 1) =
        IncomingSms(sender = sender, body = body, receivedAtMillis = at, segmentCount = segments)

    @Test
    fun `single segment is buffered then flushed when the quiet window elapses`() {
        assertNull(assembler.offer(sms(body = "merhaba")))

        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS)
        val flushed = assembler.emitExpired()

        assertEquals(1, flushed.size)
        assertEquals("merhaba", flushed[0].body)
        assertEquals("+905550001122", flushed[0].sender)
    }

    @Test
    fun `multi-PDU broadcast is emitted immediately with no delay`() {
        val completed = assembler.offer(sms(body = "complete", segments = 3))

        assertEquals("complete", completed?.body)
        assertEquals(0, assembler.emitExpired().size)
    }

    @Test
    fun `segments from one sender inside the window are joined in arrival order`() {
        assertNull(assembler.offer(sms(body = "bir ")))
        clock.advanceBy(1_000)
        assertNull(assembler.offer(sms(body = "iki ")))
        clock.advanceBy(1_000)
        assertNull(assembler.offer(sms(body = "üç")))

        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS)
        val flushed = assembler.emitExpired()

        assertEquals(1, flushed.size)
        assertEquals("bir iki üç", flushed[0].body)
        assertEquals(3, flushed[0].segmentCount)
    }

    @Test
    fun `receivedAt is the arrival time of the first segment`() {
        clock.set(10_000)
        assertNull(assembler.offer(sms(body = "a", at = 10_000)))
        clock.advanceBy(3_000)
        assertNull(assembler.offer(sms(body = "b", at = 13_000)))

        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS)
        assertEquals(10_000L, assembler.emitExpired()[0].receivedAtMillis)
    }

    @Test
    fun `interleaved senders never contaminate each other`() {
        assertNull(assembler.offer(sms(sender = "+90000000001", body = "A1")))
        clock.advanceBy(1_000)
        assertNull(assembler.offer(sms(sender = "+90000000002", body = "B1")))
        clock.advanceBy(1_000)
        assertNull(assembler.offer(sms(sender = "+90000000001", body = "A2")))
        clock.advanceBy(1_000)
        assertNull(assembler.offer(sms(sender = "+90000000002", body = "B2")))

        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS)
        val flushed = assembler.emitExpired().sortedBy { it.sender }

        assertEquals(listOf("A1A2", "B1B2"), flushed.map { it.body })
    }

    @Test
    fun `new message after a flushed window starts a fresh buffer`() {
        assertNull(assembler.offer(sms(body = "one")))
        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS + 1)
        assertEquals(1, assembler.emitExpired().size)

        assertNull(assembler.offer(sms(body = "two")))
        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS + 1)

        assertEquals(listOf("two"), assembler.emitExpired().map { it.body })
    }

    @Test
    fun `buffer is drained when the segment cap is reached`() {
        val tiny = MultipartSmsAssembler(clock, maxSegments = 2)

        assertNull(tiny.offer(sms(body = "a")))
        val completed = tiny.offer(sms(body = "b"))

        assertEquals("ab", completed?.body)
    }

    @Test
    fun `emitExpired keeps buffers that are still inside the window`() {
        assertNull(assembler.offer(sms(body = "fresh")))
        clock.advanceBy(1_000)

        assertEquals(0, assembler.emitExpired().size)
    }

    @Test
    fun `empty bodies are ignored entirely`() {
        assertNull(assembler.offer(sms(body = "")))
        clock.advanceBy(MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS + 1)

        assertEquals(0, assembler.emitExpired().size)
    }
}
