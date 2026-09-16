package com.telerelay.fakes

import com.telerelay.domain.model.SimSlot
import com.telerelay.domain.port.SimInfoProvider

/** Returns the configured SIM marker regardless of the subscription id. */
class FakeSimInfoProvider(private var marker: SimSlot? = SimSlot(slotIndex = 0)) : SimInfoProvider {

    var lastSubscriptionId: Int? = null
        private set

    fun setMarker(value: SimSlot?) {
        marker = value
    }

    override fun simMarker(subscriptionId: Int?): SimSlot? {
        lastSubscriptionId = subscriptionId
        return marker
    }
}
