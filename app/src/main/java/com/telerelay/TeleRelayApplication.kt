package com.telerelay

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt's generated component lives here and serves
 * every [dagger.hilt.android.AndroidEntryPoint] class; the Hilt worker factory
 * powers on-demand WorkManager initialisation (the default initializer is
 * removed in the manifest to keep cold start light).
 */
@HiltAndroidApp
class TeleRelayApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
