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
 * Resolves the SIM that delivered a message via [SubscriptionManager].
 * Best-effort by design: returns null without READ_PHONE_STATE, on single-SIM
 * devices (a SIM line carries no information there), and whenever the
 * delivering subscription cannot be matched — the formatter omits the marker.
 */
@Singleton
class SubscriptionSimInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SimInfoProvider {

    override fun simMarker(subscriptionId: Int?): SimSlot? {
        if (!hasReadPhoneState()) return null
        return runCatching {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return@runCatching null
            val active = manager.activeSubscriptionInfoList ?: return@runCatching null
            if (active.size <= 1) return@runCatching null
            active.firstOrNull { it.subscriptionId == subscriptionId }?.let { subscription ->
                SimSlot(slotIndex = subscription.simSlotIndex.coerceAtLeast(0))
            }
        }.getOrNull()
    }

    private fun hasReadPhoneState(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED
}
