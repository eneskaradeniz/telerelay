package com.telerelay.data.telegram

import com.telerelay.data.telegram.dto.SendMessageRequest
import com.telerelay.data.telegram.dto.TelegramResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/** Retrofit declaration of the single Bot API endpoint TeleRelay uses. */
interface TelegramApi {

    /**
     * The token lives in the URL path per Bot API convention
     * (`https://api.telegram.org/bot<token>/sendMessage`). The full absolute
     * URL is built by the gateway: a token contains ':', which Retrofit cannot
     * resolve inside a RELATIVE path (it parses as a URI scheme). An absolute
     * @Url keeps the raw, documented token format AND lets tests point the
     * call at a mock server.
     *
     * It must never be logged — the app ships without any HTTP logging interceptor.
     */
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Body request: SendMessageRequest,
    ): TelegramResponse
}
