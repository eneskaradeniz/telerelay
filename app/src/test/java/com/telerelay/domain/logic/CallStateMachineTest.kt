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
}
