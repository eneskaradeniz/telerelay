package com.telerelay.data.sim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.telerelay.domain.model.SimSlot
import com.telerelay.domain.port.SimInfoProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the active SIM via [SubscriptionManager]. Best-effort by design:
 * returns null without READ_PHONE_STATE, on single-SIM devices, and whenever
 * the platform declines to answer — the formatter simply omits the SIM line.
 */
@Singleton
class SubscriptionSimInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SimInfoProvider {

    override fun activeSim(): SimSlot? {
        if (!hasReadPhoneState()) return null
        return runCatching {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return@runCatching null
            val subscription = manager.activeSubscriptionInfoList?.firstOrNull() ?: return@runCatching null
            SimSlot(
                slotIndex = subscription.simSlotIndex.coerceAtLeast(0),
                carrierName = subscription.displayName?.toString()?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private fun hasReadPhoneState(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED
}
