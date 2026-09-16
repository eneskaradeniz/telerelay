package com.telerelay.domain.model

/** Result of one attempt to deliver a message to Telegram. */
sealed interface SendOutcome {

    /** Delivered. */
    data object Sent : SendOutcome

    /**
     * Not delivered; the caller should re-try later.
     * @param delaySeconds server-suggested delay (Telegram's `retry_after`), or null
     *   to let the retry mechanism use its own exponential backoff.
     */
    data class RetryLater(val delaySeconds: Long?) : SendOutcome

    /** Not delivered and never will be with the current configuration. */
    data class Failed(val reason: FailureReason) : SendOutcome
}

/** Permanent failure causes. Surfaced to the user; never retried. */
enum class FailureReason {
    /** 401 from Telegram: the bot token is wrong or revoked. */
    INVALID_TOKEN,

    /** 400 "chat not found": the chat ID is wrong or the bot was never started. */
    INVALID_CHAT,

    /** Credentials missing locally. */
    NOT_CONFIGURED,

    /** Any other permanent rejection. */
    REJECTED,
}
