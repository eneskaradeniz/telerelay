package com.telerelay.domain.port

/**
 * Best-effort contact display name for a phone number. Returns null when the
 * number is not in the device contacts, when the contacts permission is not
 * granted, or when the lookup fails for any reason — callers fall back to the
 * raw number.
 */
interface ContactNameResolver {
    fun resolve(phoneNumber: String): String?
}
