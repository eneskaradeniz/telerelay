package com.telerelay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.service.MonitorService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Restarts call monitoring after a reboot when the feature is enabled.
 *
 * Note: the platform only delivers BOOT_COMPLETED to an app the user has opened
 * at least once since installation — documented in the README.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val s = settings.current()
        if (!s.isConfigured || !s.callNotificationEnabled) return

        context.startForegroundService(Intent(context, MonitorService::class.java))
    }
}
