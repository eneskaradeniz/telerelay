package com.telerelay.data.crypto

/** Encrypts/decrypts short secret strings (bot token, chat ID) at rest. */
interface SecretCipher {
    /** @return Base64(iv ‖ ciphertext) — deterministic storage format, random IV. */
    fun encrypt(plain: String): String

    /** @return the plaintext, or null when the blob cannot be decrypted (key lost, corrupt input). */
    fun decrypt(blob: String): String?
}
