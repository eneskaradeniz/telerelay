package com.telerelay.domain.port

import com.telerelay.domain.model.SimSlot

/** Best-effort lookup of the active SIM. Returns null when unknown/unavailable. */
interface SimInfoProvider {
    fun activeSim(): SimSlot?
}
