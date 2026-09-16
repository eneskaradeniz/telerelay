package com.telerelay.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.telerelay.data.crypto.SecretCipher
import com.telerelay.domain.model.SendOutcome
import com.telerelay.domain.port.MessageSender
import com.telerelay.domain.port.TelegramGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends immediately when possible; on transient failure persists the message
 * into a unique WorkManager queue so a burst of unreachable network still
 * delivers, in order, once connectivity returns.
 *
 * CONNECTED (not UNMETERED): a ~100-byte Telegram call is fine over mobile data
 * and metered-only devices must not stall the queue.
 */
@Singleton
class WorkManagerMessageSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: TelegramGateway,
    private val cipher: SecretCipher,
) : MessageSender {

    override suspend fun sendOrEnqueue(text: String) {
        when (gateway.send(text)) {
            is SendOutcome.Sent -> Unit
            is SendOutcome.RetryLater -> enqueue(text)
            // Permanent (bad token / chat): retrying cannot help; the settings
            // screen surfaces the configuration error to the user instead.
            is SendOutcome.Failed -> Unit
        }
    }

    private fun enqueue(text: String) {
        // WorkManager persists its Data payload to disk — store the message
        // encrypted so SMS content never lands in plaintext on the device.
        val request = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(workDataOf(ForwardWorker.KEY_TEXT to cipher.encrypt(text)))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_OUTBOX,
            // APPEND keeps FIFO order and stops parallel workers racing the 1 msg/s limit.
            ExistingWorkPolicy.APPEND,
            request,
        )
    }

    companion object {
        const val UNIQUE_OUTBOX = "telerelay.outbox"
        const val INITIAL_BACKOFF_SECONDS = 10L
    }
}
