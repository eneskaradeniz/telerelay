package com.telerelay.data.telegram

import com.telerelay.data.telegram.dto.CopyTextButton
import com.telerelay.data.telegram.dto.InlineKeyboardButton
import com.telerelay.data.telegram.dto.InlineKeyboardMarkup
import com.telerelay.data.telegram.dto.SendMessageRequest
import com.telerelay.data.telegram.dto.TelegramResponse
import com.telerelay.di.IoDispatcher
import com.telerelay.di.TelegramBaseUrl
import com.telerelay.domain.model.FailureReason
import com.telerelay.domain.model.OutgoingMessage
import com.telerelay.domain.model.SendOutcome
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.domain.port.TelegramGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends one message per invocation and translates every failure mode into a
 * [SendOutcome]: callers never see exceptions and never need to know HTTP details.
 *
 * Telegram answers errors two ways — HTTP 4xx/5xx with a JSON body, and HTTP 200
 * with `ok:false` — so both layers are always inspected.
 */
@Singleton
class TelegramGatewayImpl @Inject constructor(
    private val api: TelegramApi,
    private val json: Json,
    private val settings: SettingsRepository,
    private val rateLimiter: RateLimiter,
    @TelegramBaseUrl private val baseUrl: String,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TelegramGateway {

    override suspend fun send(message: OutgoingMessage): SendOutcome = withContext(ioDispatcher) {
        val s = settings.current()
        val token = s.botToken ?: return@withContext SendOutcome.Failed(FailureReason.NOT_CONFIGURED)
        val chatId = s.chatId ?: return@withContext SendOutcome.Failed(FailureReason.NOT_CONFIGURED)

        rateLimiter.withPermit {
            try {
                val url = "${baseUrl}bot$token/sendMessage"
                val request = SendMessageRequest(
                    chatId = chatId,
                    text = message.text,
                    parseMode = HTML_PARSE_MODE,
                    replyMarkup = message.copyText?.let { copyButton(it) },
                )
                respondToOutcome(api.sendMessage(url, request))
            } catch (e: HttpException) {
                // Non-2xx: try to recover the standard error envelope from the body.
                val parsed = e.response()?.errorBody()
                    ?.string()
                    ?.let { body -> runCatching { json.decodeFromString<TelegramResponse>(body) }.getOrNull() }
                parsed?.let(::respondToOutcome) ?: SendOutcome.RetryLater(delaySeconds = null)
            } catch (e: IOException) {
                // Network unreachable / timeout — transient by definition.
                SendOutcome.RetryLater(delaySeconds = null)
            } catch (e: Exception) {
                // Defensive: forwarding must never crash the receiving process.
                SendOutcome.RetryLater(delaySeconds = null)
            }
        }
    }

    /** One-tap copy button (Bot API 7.10+) so a detected code needs no manual selection. */
    private fun copyButton(copyText: String) = InlineKeyboardMarkup(
        inlineKeyboard = listOf(
            listOf(
                InlineKeyboardButton(text = COPY_BUTTON_LABEL, copyText = CopyTextButton(text = copyText)),
            ),
        ),
    )

    private fun respondToOutcome(response: TelegramResponse): SendOutcome = when {
        response.ok -> SendOutcome.Sent

        response.errorCode == HTTP_UNAUTHORIZED ->
            SendOutcome.Failed(FailureReason.INVALID_TOKEN)

        response.errorCode == HTTP_BAD_REQUEST && response.description.orEmpty().contains("chat not found", ignoreCase = true) ->
            SendOutcome.Failed(FailureReason.INVALID_CHAT)

        response.errorCode == HTTP_TOO_MANY_REQUESTS || response.parameters?.retryAfter != null ->
            SendOutcome.RetryLater(delaySeconds = response.parameters?.retryAfter?.toLong() ?: DEFAULT_RETRY_SECONDS)

        else -> SendOutcome.RetryLater(delaySeconds = null)
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DEFAULT_RETRY_SECONDS = 5L

        /** Message bodies are HTML-formatted by the domain formatter. */
        const val HTML_PARSE_MODE = "HTML"

        /** Fixed Turkish label — relayed message formats are intentionally not localized. */
        const val COPY_BUTTON_LABEL = "Kodu kopyala"
    }
}
