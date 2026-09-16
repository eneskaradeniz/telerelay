package com.telerelay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.telerelay.R
import com.telerelay.di.ApplicationScope
import com.telerelay.domain.port.CallMonitor
import com.telerelay.domain.usecase.ForwardCallUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service whose only job is to keep the process alive so the
 * [CallMonitor] keeps receiving call-state callbacks. SMS forwarding does NOT
 * depend on it — the SMS broadcast wakes the process by itself.
 *
 * The `specialUse` foreground service type is deliberate: the other types are
 * either semantically wrong or carry runtime limits (dataSync is killed after
 * 6h/24h since Android 15), while this service must run indefinitely by design.
 */
@AndroidEntryPoint
class MonitorService : Service() {

    @Inject lateinit var callMonitor: CallMonitor
    @Inject lateinit var forwardCall: ForwardCallUseCase
    @Inject lateinit var monitorStatus: MonitorStatus
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must reach startForeground() within seconds of start or the system
        // crashes the process — no I/O before this line, by contract.
        startInForeground()

        callMonitor.start { event ->
            scope.launch { forwardCall.onEvent(event) }
        }
        monitorStatus.markStarted()
        return START_STICKY
    }

    override fun onDestroy() {
        callMonitor.stop()
        forwardCall.onMonitoringRestarted()
        monitorStatus.markStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(getString(R.string.notif_monitor_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "telerelay.monitor"
        const val NOTIFICATION_ID = 1001
    }
}
