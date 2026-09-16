package com.telerelay.domain.port

import com.telerelay.domain.model.SimSlot

/**
 * Best-effort lookup of the SIM that delivered a message.
 *
 * Returns null — and the formatter omits the SIM marker — on single-SIM devices
 * (a SIM line carries no information there) and whenever the delivering
 * subscription cannot be resolved. Showing a wrong SIM is worse than showing
 * none.
 */
interface SimInfoProvider {

    /**
     * @param subscriptionId subscription that delivered the SMS, from the
     *   broadcast extra; null when unknown (e.g. the call pipeline, whose
     *   ringing event carries no subscription on the public API).
     */
    fun simMarker(subscriptionId: Int?): SimSlot?
}
