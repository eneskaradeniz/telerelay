package com.telerelay.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.telerelay.data.crypto.SecretCipher
import com.telerelay.domain.model.SendOutcome
import com.telerelay.domain.port.TelegramGateway
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Retries one message delivery through WorkManager. Scheduled only after a
 * direct attempt reported a transient failure; permanent failures are never
 * retried.
 */
@HiltWorker
class ForwardWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val gateway: TelegramGateway,
    private val cipher: SecretCipher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The payload was encrypted at enqueue time; an undecryptable payload
        // means the Keystore key was lost — retrying cannot recover it.
        val text = inputData.getString(KEY_TEXT)
            ?.let(cipher::decrypt)
            ?: return Result.failure()
        return when (gateway.send(text)) {
            is SendOutcome.Sent -> Result.success()
            is SendOutcome.RetryLater -> Result.retry()
            is SendOutcome.Failed -> Result.failure()
        }
    }

    companion object {
        const val KEY_TEXT = "text"
    }
}
