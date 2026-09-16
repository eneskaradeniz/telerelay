package com.telerelay.data.contact

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.telerelay.domain.port.ContactNameResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a phone number to its contact display name through the
 * [ContactsContract.PhoneLookup] filter URI — the platform's index-backed
 * number matcher, so formatting differences between the stored number and the
 * PDU's address are tolerated. Best-effort: returns null without
 * READ_CONTACTS, for unknown numbers, and on any lookup failure.
 */
@Singleton
class PhoneLookupContactNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactNameResolver {

    override fun resolve(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (!hasReadContacts()) return null
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private fun hasReadContacts(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
}
