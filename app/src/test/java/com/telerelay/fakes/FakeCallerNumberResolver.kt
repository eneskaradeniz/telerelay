package com.telerelay.fakes

import com.telerelay.domain.port.CallerNumberResolver

/** Returns the configured number (simulating a call-log row), or null. */
class FakeCallerNumberResolver(private var resolved: String? = null) : CallerNumberResolver {

    fun setResolved(value: String?) {
        resolved = value
    }

    override suspend fun resolve(sinceMillis: Long): String? = resolved
}
