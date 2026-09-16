package com.telerelay.domain.port

import com.telerelay.domain.model.FilterDecision

/**
 * Inspects an SMS body before forwarding. Implementations must be cheap, never
 * throw, and never depend on Android classes (unit-test target).
 */
interface PrivacyFilter {
    fun evaluate(sender: String, body: String): FilterDecision
}
