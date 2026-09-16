package com.telerelay.data.telegram

import com.telerelay.data.privacy.FakeSettingsRepository
import com.telerelay.domain.model.AppSettings
import com.telerelay.domain.model.FailureReason
import com.telerelay.domain.model.SendOutcome
import com.telerelay.fakes.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TelegramGatewayImplTest {

    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettingsRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Fresh credentials per test: one test intentionally blanks them, and a
        // shared instance would leak that state into later tests.
        settings = FakeSettingsRepository(
            AppSettings(botToken = "123:SECRET", chatId = "42"),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun gateway(): TelegramGatewayImpl {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return TelegramGatewayImpl(
            api = retrofit.create(TelegramApi::class.java),
            json = json,
            settings = settings,
            rateLimiter = RateLimiter(FakeClock()),
            baseUrl = server.url("/").toString(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    @Test
    fun `success maps to Sent`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"message_id":1}}"""))

        assertEquals(SendOutcome.Sent, gateway().send("hello"))
    }

    @Test
    fun `http 429 with retry_after maps to RetryLater with server delay`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"ok":false,"error_code":429,"description":"Too Many Requests","parameters":{"retry_after":2}}"""),
        )

        val outcome = gateway().send("hello")
        assertEquals(SendOutcome.RetryLater(delaySeconds = 2), outcome)
    }

    @Test
    fun `http 200 with ok false and 429 maps to RetryLater`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":false,"error_code":429,"description":"Too Many Requests","parameters":{"retry_after":7}}""",
            ),
        )

        assertEquals(SendOutcome.RetryLater(delaySeconds = 7), gateway().send("hello"))
    }

    @Test
    fun `http 401 maps to permanent invalid token failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"ok":false,"error_code":401,"description":"Unauthorized"}"""),
        )

        val outcome = gateway().send("hello")
        assertEquals(SendOutcome.Failed(FailureReason.INVALID_TOKEN), outcome)
    }

    @Test
    fun `chat not found maps to permanent invalid chat failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""",
            ),
        )

        assertEquals(SendOutcome.Failed(FailureReason.INVALID_CHAT), gateway().send("hello"))
    }

    @Test
    fun `server error maps to retryable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(SendOutcome.RetryLater(delaySeconds = null), gateway().send("hello"))
    }

    @Test
    fun `unreachable server maps to retryable and never throws`() = runBlocking {
        val gateway = gateway()
        server.shutdown()

        assertEquals(SendOutcome.RetryLater(delaySeconds = null), gateway.send("hello"))
    }

    @Test
    fun `missing configuration short circuits without a network call`() = runBlocking {
        settings.set(AppSettings(botToken = null, chatId = null))

        val outcome = gateway().send("hello")
        assertEquals(SendOutcome.Failed(FailureReason.NOT_CONFIGURED), outcome)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `request hits the bot-token path with chat_id and text in the body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"message_id":1}}"""))

        gateway().send("hello")

        val recorded = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)
            ?: error("no request reached the mock server within 10 s")
        assertEquals("/bot123:SECRET/sendMessage", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"chat_id\":\"42\""))
        assertTrue(body.contains("\"text\":\"hello\""))
    }
}
