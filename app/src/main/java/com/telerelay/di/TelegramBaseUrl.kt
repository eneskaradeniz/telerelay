package com.telerelay.di

import javax.inject.Qualifier

/** Qualifies the Telegram Bot API base URL string binding. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramBaseUrl
