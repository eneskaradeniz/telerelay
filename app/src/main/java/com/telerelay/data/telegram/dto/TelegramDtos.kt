package com.telerelay.data.telegram.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body of the Bot API `sendMessage` call. */
@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: String,
    val text: String,
)

/**
 * Telegram Bot API envelope. Note the API can answer HTTP 200 with `ok=false`,
 * and HTTP errors also carry this body — both paths are interpreted.
 */
@Serializable
data class TelegramResponse(
    val ok: Boolean,
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
    val parameters: ResponseParameters? = null,
)

/** Extra parameters Telegram attaches to some errors (e.g. flood waits). */
@Serializable
data class ResponseParameters(
    @SerialName("migrate_to_chat_id") val migrateToChatId: Long? = null,
    @SerialName("retry_after") val retryAfter: Int? = null,
)
