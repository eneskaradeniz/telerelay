package com.telerelay.data.telephony

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.telerelay.domain.model.CallState
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.domain.port.CallMonitor
import com.telerelay.domain.port.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Call monitoring for API 26–30 where [PhoneStateListener] is the only option.
 * Unlike the modern path, the platform DOES deliver the caller number here.
 */
@Suppress("DEPRECATION")
@Singleton
class LegacyPhoneStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : CallMonitor {

    private var listener: Listener? = null

    override fun start(onEvent: (CallStateEvent) -> Unit) {
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return
        val listener = Listener(clock, onEvent)
        runCatching { manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
            .onSuccess { this.listener = listener }
            .onFailure { this.listener = null }
    }

    override fun stop() {
        val manager = context.getSystemService(TelephonyManager::class.java)
        listener?.let { listener ->
            runCatching { manager?.listen(listener, PhoneStateListener.LISTEN_NONE) }
        }
        listener = null
    }

    private class Listener(
        private val clock: Clock,
        private val onEvent: (CallStateEvent) -> Unit,
    ) : PhoneStateListener() {

        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            val callState = when (state) {
                TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
                TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
                else -> CallState.IDLE
            }
            onEvent(
                CallStateEvent(
                    state = callState,
                    number = phoneNumber?.takeIf { it.isNotBlank() },
                    atMillis = clock.nowMillis(),
                ),
            )
        }
    }
}
