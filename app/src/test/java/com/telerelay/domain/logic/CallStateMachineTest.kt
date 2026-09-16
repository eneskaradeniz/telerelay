package com.telerelay.domain.logic

import com.telerelay.domain.model.CallNotification
import com.telerelay.domain.model.CallState
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallStateMachineTest {

    private val clock = FakeClock()
    private val machine = CallStateMachine(clock)

    private fun event(state: CallState, number: String? = null) =
        CallStateEvent(state = state, number = number, atMillis = clock.nowMillis())

    private fun ring(at: Long = clock.nowMillis(), number: String? = null): CallNotification? {
        clock.set(at)
        return machine.onEvent(event(CallState.RINGING, number))
    }

    @Test
    fun `cold start ringing emits an incoming notification`() {
        val notification = ring(at = 1_000, number = "+905550001122")

        assertEquals(CallNotification.Incoming("+905550001122", 1_000), notification)
    }

    @Test
    fun `cold start idle emits nothing`() {
        assertNull(machine.onEvent(event(CallState.IDLE)))
    }

    @Test
    fun `duplicate ringing is ignored`() {
        ring(at = 1_000)
        clock.set(2_000)
        assertNull(machine.onEvent(event(CallState.RINGING)))
    }

    @Test
    fun `ringing then offhook then idle is an answered call and emits nothing more`() {
        ring(at = 1_000)
        clock.set(10_000)
        assertNull(machine.onEvent(event(CallState.OFFHOOK)))

        clock.set(30_000)
        assertNull(machine.onEvent(event(CallState.IDLE)))
    }

    @Test
    fun `ringing then idle without answer emits missed with the ringing number`() {
        ring(at = 1_000, number = "+905550001122")
        clock.set(25_000)
        val notification = machine.onEvent(event(CallState.IDLE))

        assertEquals(CallNotification.Missed("+905550001122"), notification)
    }

    @Test
    fun `very short ring before idle is treated as flicker and emits nothing`() {
        ring(at = 1_000)
        clock.set(1_500) // 500 ms "ring"
        assertNull(machine.onEvent(event(CallState.IDLE)))
    }

    @Test
    fun `incoming and missed pair for the same call is never doubled by flicker`() {
        ring(at = 1_000)
        clock.set(30_000) // long enough to count as a real ring
        assertEquals(CallNotification.Missed(null), machine.onEvent(event(CallState.IDLE)))
    }

    @Test
    fun `a second ring inside the cooldown is suppressed as flicker`() {
        ring(at = 1_000, number = "+905550001122")
        clock.set(2_000)
        assertNull(machine.onEvent(event(CallState.IDLE))) // flicker gap

        val notification = ring(at = 3_000, number = "+905550001122") // same call re-delivered
        assertNull(notification)
    }

    @Test
    fun `a second ring after the cooldown emits a new incoming notification`() {
        ring(at = 1_000, number = "+905550001122")
        clock.set(2_000)
        machine.onEvent(event(CallState.IDLE))

        clock.set(10_000) // past the 5 s cooldown
        val notification = ring(at = 10_000, number = "+905550009988")

        assertEquals(CallNotification.Incoming("+905550009988", 10_000), notification)
    }

    @Test
    fun `outgoing call from idle emits nothing`() {
        clock.set(1_000)
        assertNull(machine.onEvent(event(CallState.OFFHOOK)))

        clock.set(20_000)
        assertNull(machine.onEvent(event(CallState.IDLE)))
    }

    @Test
    fun `ringing while offhook is call waiting and emits nothing`() {
        clock.set(1_000)
        assertNull(machine.onEvent(event(CallState.OFFHOOK))) // ongoing call

        clock.set(2_000)
        assertNull(machine.onEvent(event(CallState.RINGING)))
    }

    @Test
    fun `reset forgets the previous ring`() {
        ring(at = 1_000, number = "+905550001122")
        machine.reset()

        clock.set(20_000)
        val notification = machine.onEvent(event(CallState.IDLE))

        assertNull(notification)
    }

    // --- broadcast-captured numbers (the API 31+ ringing-time source) --------

    @Test
    fun `broadcast number arriving before ringing is attached to the incoming notification`() {
        machine.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 900)

        val notification = ring(at = 1_000)

        assertEquals(CallNotification.Incoming("+905550001122", 1_000), notification)
    }

    @Test
    fun `stale broadcast number is ignored`() {
        machine.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 0)

        val notification = ring(at = 20_000) // beyond the freshness window

        assertEquals(CallNotification.Incoming(null, 20_000), notification)
    }

    @Test
    fun `blank broadcast numbers are ignored`() {
        machine.onIncomingNumber(CallState.RINGING, "  ", atMillis = 900)

        val notification = ring(at = 1_000)

        assertEquals(CallNotification.Incoming(null, 1_000), notification)
    }

    @Test
    fun `numbers riding an idle or offhook broadcast are ignored`() {
        // The IDLE broadcast of a finished call often still carries its number;
        // storing it would mislabel the NEXT caller.
        machine.onIncomingNumber(CallState.IDLE, "+905550001122", atMillis = 300)
        machine.onIncomingNumber(CallState.OFFHOOK, "+905550009988", atMillis = 400)

        val notification = ring(at = 5_000) // well within the freshness window

        assertEquals(CallNotification.Incoming(null, 5_000), notification)
    }

    @Test
    fun `broadcast number landing during the ring is used by the missed notification`() {
        assertEquals(CallNotification.Incoming(null, 1_000), ring(at = 1_000))

        machine.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 2_000)

        clock.set(25_000) // ring lasted 24 s — beyond freshness, but mid-ring arrival wins
        assertEquals(CallNotification.Missed("+905550001122"), machine.onEvent(event(CallState.IDLE)))
    }

    @Test
    fun `unconsumed mid-ring number from an answered call does not attach to the next ring`() {
        ring(at = 1_000) // no number at ring time
        machine.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 2_000) // lands mid-ring
        clock.set(6_000)
        machine.onEvent(event(CallState.OFFHOOK)) // call A answered — number never consumed
        clock.set(20_000)
        machine.onEvent(event(CallState.IDLE)) // call A ends

        clock.set(30_000)
        val notification = ring(at = 30_000) // call B

        assertEquals(CallNotification.Incoming(null, 30_000), notification)
    }

    @Test
    fun `broadcast number is consumed once`() {
        machine.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 900)

        ring(at = 1_000)

        assertNull(machine.takeBroadcastNumber(nowMillis = 1_500, forRingStart = false))
    }

    @Test
    fun `reset forgets the broadcast number`() {
        machine.onIncomingNumber(CallState.RINGING, "+905550001122", atMillis = 900)
        machine.reset()

        assertNull(machine.peekBroadcastNumber(nowMillis = 1_000))
    }
}
