package com.telerelay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.telerelay.di.ApplicationScope
import com.telerelay.domain.model.CallState
import com.telerelay.domain.port.Clock
import com.telerelay.domain.usecase.ForwardCallUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Captures the caller number from the PHONE_STATE broadcast — on API 31+ the
 * only ringing-time channel that still carries it for apps holding
 * READ_CALL_LOG (the telephony callback no longer does, and the call-log row
 * is written only when the call ends). Thin by contract: read the extras,
 * delegate, return. The state rides along so the pipeline can reject numbers
 * belonging to calls that already ended.
 */
@AndroidEntryPoint
class PhoneStateReceiver : BroadcastReceiver() {

    @Inject lateinit var forwardCall: ForwardCallUseCase
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> CallState.RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> CallState.OFFHOOK
            else -> CallState.IDLE
        }
        scope.launch { forwardCall.onIncomingNumber(state, number, clock.nowMillis()) }
    }
}
