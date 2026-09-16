package com.telerelay.domain.model

/**
 * The SIM (subscription) that delivered a message, when the platform exposes it.
 * `null` everywhere in the app means "unknown / single SIM" and the formatter omits it.
 */
data class SimSlot(
    val slotIndex: Int,
) {
    /** Human-readable label such as "SIM1" or "SIM2". */
    val label: String get() = "SIM${slotIndex + 1}"
}
