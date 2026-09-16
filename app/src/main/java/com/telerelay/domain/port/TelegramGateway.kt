package com.telerelay.domain.port

import com.telerelay.domain.model.OutgoingMessage
import com.telerelay.domain.model.SendOutcome

/** Delivers a formatted message to the user's Telegram chat. */
interface TelegramGateway {

    /**
     * Performs exactly one delivery attempt. Never throws for network/API
     * conditions — failures are expressed as [SendOutcome].
     */
    suspend fun send(message: OutgoingMessage): SendOutcome
}
