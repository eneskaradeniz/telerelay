package com.telerelay.fakes

import com.telerelay.domain.model.SimSlot
import com.telerelay.domain.port.MessageFormatter

/** Records the arguments and renders them into predictable strings. */
class FakeMessageFormatter : MessageFormatter {

    var lastSmsSender: String? = null
        private set
    var lastSmsBody: String? = null
        private set
    var lastSmsSim: SimSlot? = null
        private set
    var lastCallCaller: String? = null
        private set
    var lastMissedCaller: String? = null
        private set

    fun reset() {
        lastSmsSender = null
        lastSmsBody = null
        lastSmsSim = null
        lastCallCaller = null
        lastMissedCaller = null
    }

    override fun sms(sender: String?, body: String, sim: SimSlot?): String {
        lastSmsSender = sender
        lastSmsBody = body
        lastSmsSim = sim
        return "text:$sender|$body"
    }

    override fun incomingCall(caller: String?): String {
        lastCallCaller = caller
        return "call:$caller"
    }

    override fun missedCall(caller: String?): String {
        lastMissedCaller = caller
        return "missed:$caller"
    }

    override fun testMessage(): String = "test"
}
