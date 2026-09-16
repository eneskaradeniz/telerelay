package com.telerelay.data.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.telerelay.di.IoDispatcher
import com.telerelay.domain.port.CallerNumberResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves caller numbers from the call log — the only source on API 31+.
 *
 * The log row for a call is written asynchronously (usually right when the
 * phone starts ringing, always by the time it ends), so [resolve] polls a few
 * times before giving up and returning null ("Bilinmiyor").
 *
 * Numbers are read transiently and never stored; the query excludes outgoing
 * rows so a previous outgoing call can never be misreported as the caller.
 */
@Singleton
class CallLogNumberResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CallerNumberResolver {

    override suspend fun resolve(sinceMillis: Long): String? = withContext(ioDispatcher) {
        repeat(RESOLVE_ATTEMPTS) { attempt ->
            queryLatest(sinceMillis)?.let { return@withContext it }
            if (attempt < RESOLVE_ATTEMPTS - 1) delay(RESOLVE_DELAY_MILLIS)
        }
        null
    }

    private fun queryLatest(sinceMillis: Long): String? {
        if (!hasReadCallLog()) return null
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} <> ?",
                arrayOf(sinceMillis.toString(), CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private fun hasReadCallLog(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALL_LOG,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val RESOLVE_ATTEMPTS = 4
        const val RESOLVE_DELAY_MILLIS = 500L
    }
}
