package com.telerelay.domain.port

/**
 * Best-effort lookup of the number of a call that rang at/after the given
 * instant. Needed because API 31+ call-state callbacks do not carry the number.
 *
 * Implementations poll internally (the call-log row is written asynchronously)
 * and return null when the number cannot be determined — callers then show
 * "Bilinmiyor". Requires READ_CALL_LOG on API 29+.
 */
interface CallerNumberResolver {
    suspend fun resolve(sinceMillis: Long): String?
}
