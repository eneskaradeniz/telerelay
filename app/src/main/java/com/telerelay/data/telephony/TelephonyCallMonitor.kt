package com.telerelay.data.telephony

import android.content.Context
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.telerelay.domain.model.CallState
import com.telerelay.domain.model.CallStateEvent
import com.telerelay.domain.port.CallMonitor
import com.telerelay.domain.port.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Call monitoring for API 31+. The platform no longer reports the caller number
 * here (by design) — [com.telerelay.domain.port.CallerNumberResolver] fills the gap.
 * Callbacks arrive on [Context.mainExecutor]; registration is guarded so a
 * missing permission degrades to "no events" instead of crashing.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Singleton
class TelephonyCallMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : CallMonitor {

    private var callback: Callback? = null

    override fun start(onEvent: (CallStateEvent) -> Unit) {
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return
        val callback = Callback(clock, onEvent)
        runCatching { manager.registerTelephonyCallback(context.mainExecutor, callback) }
            .onSuccess { this.callback = callback }
            .onFailure { this.callback = null } // SecurityException without READ_PHONE_STATE
    }

    override fun stop() {
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return
        callback?.let { runCatching { manager.unregisterTelephonyCallback(it) } }
        callback = null
    }

    /** Must be a class: TelephonyCallback is abstract and the listener interface rides on it. */
    @RequiresApi(Build.VERSION_CODES.S)
    private class Callback(
        private val clock: Clock,
        private val onEvent: (CallStateEvent) -> Unit,
    ) : TelephonyCallback(), TelephonyCallback.CallStateListener {

        override fun onCallStateChanged(state: Int) {
            val callState = when (state) {
                TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
                TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
                else -> CallState.IDLE
            }
            onEvent(CallStateEvent(state = callState, number = null, atMillis = clock.nowMillis()))
        }
    }
}
