package com.telerelay.data.telegram

import com.telerelay.data.telegram.dto.SendMessageRequest
import com.telerelay.data.telegram.dto.TelegramResponse
import com.telerelay.di.IoDispatcher
import com.telerelay.di.TelegramBaseUrl
import com.telerelay.domain.model.FailureReason
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

    override suspend fun send(text: String): SendOutcome = withContext(ioDispatcher) {
        val s = settings.current()
        val token = s.botToken ?: return@withContext SendOutcome.Failed(FailureReason.NOT_CONFIGURED)
        val chatId = s.chatId ?: return@withContext SendOutcome.Failed(FailureReason.NOT_CONFIGURED)

        rateLimiter.withPermit {
            try {
                val url = "${baseUrl}bot$token/sendMessage"
                respondToOutcome(api.sendMessage(url, SendMessageRequest(chatId = chatId, text = text)))
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
    }
}
