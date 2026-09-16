package com.telerelay.di

import com.telerelay.data.telegram.TelegramApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network graph. Deliberately minimal and privacy-preserving:
 * a single base URL, no cookies, and NO logging interceptor — the bot token
 * travels in the URL path and must never end up in a log line.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TELEGRAM_BASE_URL = "https://api.telegram.org/"

    @Provides
    @TelegramBaseUrl
    fun telegramBaseUrl(): String = TELEGRAM_BASE_URL

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(TELEGRAM_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun telegramApi(retrofit: Retrofit): TelegramApi = retrofit.create(TelegramApi::class.java)
}
