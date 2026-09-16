package com.telerelay.fakes

import com.telerelay.domain.port.ContactNameResolver

/** In-memory contact lookup for unit tests; unset numbers resolve to null. */
class FakeContactNameResolver : ContactNameResolver {

    val directory = mutableMapOf<String, String>()

    override fun resolve(phoneNumber: String): String? = directory[phoneNumber]
}
