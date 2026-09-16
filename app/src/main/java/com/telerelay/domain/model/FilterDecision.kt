package com.telerelay.domain.model

/** Outcome of running a message body through the privacy guard. */
sealed interface FilterDecision {
    /** Forward the body unchanged. */
    data object Allow : FilterDecision

    /** Do not forward at all. */
    data object Exclude : FilterDecision

    /** Forward with the matched secrets replaced (same length) by bullets. */
    data class Masked(val maskedBody: String) : FilterDecision
}
