package com.telerelay.data.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.telerelay.domain.port.ServiceController
import com.telerelay.service.MonitorService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Turns ServiceController calls into foreground-service intents. */
@Singleton
class AndroidServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServiceController {

    override fun startCallMonitoring() {
        ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
    }

    override fun stopCallMonitoring() {
        context.stopService(Intent(context, MonitorService::class.java))
    }
}
