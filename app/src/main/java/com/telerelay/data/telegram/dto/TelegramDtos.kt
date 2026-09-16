package com.telerelay.data.telegram.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body of the Bot API `sendMessage` call. Messages are always sent with
 * HTML parse mode (the formatter emits `<b>` sender lines); [replyMarkup] is
 * present only when a one-tap copy button (OTP codes) was detected.
 */
@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: String,
    val text: String,
    @SerialName("parse_mode") val parseMode: String? = null,
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null,
)

/** Inline keyboard carrying one or more buttons attached to a message. */
@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>,
)

@Serializable
data class InlineKeyboardButton(
    val text: String,
    /** Bot API 7.10+ one-tap copy action; mutually exclusive with URL-style actions. */
    @SerialName("copy_text") val copyText: CopyTextButton? = null,
)

@Serializable
data class CopyTextButton(val text: String)

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
