package com.telerelay.domain.model

/**
 * The SIM (subscription) a message or call arrived on, when the device exposes it.
 * `null` everywhere in the app means "unknown / single SIM" and the formatter omits it.
 */
data class SimSlot(
    val slotIndex: Int,
    val carrierName: String?,
) {
    /** Human-readable label such as "SIM1" or "SIM2". */
    val label: String get() = "SIM${slotIndex + 1}"
}
