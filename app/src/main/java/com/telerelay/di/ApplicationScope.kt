package com.telerelay.di

import javax.inject.Qualifier

/**
 * Marks the application-lifetime coroutine scope. Receivers hand work off to it
 * so processing outlives the broadcast window without [android.content.BroadcastReceiver.goAsync].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
