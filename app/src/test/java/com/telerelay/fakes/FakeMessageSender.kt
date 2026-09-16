package com.telerelay.fakes

import com.telerelay.domain.model.OutgoingMessage
import com.telerelay.domain.port.MessageSender

/** Records outgoing messages instead of delivering them. */
class FakeMessageSender : MessageSender {

    val sent = mutableListOf<OutgoingMessage>()

    override suspend fun sendOrEnqueue(message: OutgoingMessage) {
        sent += message
    }
}
