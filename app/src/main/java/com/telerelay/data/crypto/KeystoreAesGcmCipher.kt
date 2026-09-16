package com.telerelay.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-GCM cipher backed by a non-exportable Android Keystore key.
 *
 * Deliberately hand-rolled instead of using the (deprecated) Jetpack
 * security-crypto library: one key, one transformation, ~60 lines, no
 * additional dependencies, and every failure mode is explicit.
 *
 * - key never leaves the secure hardware/TEE when available;
 * - [encrypt] generates a fresh random IV per call (GCM requirement);
 * - [decrypt] never throws — a lost key (device transfer, KeyStore reset)
 *   degrades to "secret not configured" and the UI asks for re-entry.
 */
@Singleton
class KeystoreAesGcmCipher @Inject constructor() : SecretCipher {

    private val key: SecretKey by lazy { getOrCreateKey() }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    override fun decrypt(blob: String): String? = runCatching {
        val data = Base64.decode(blob, Base64.NO_WRAP)
        require(data.size > IV_LENGTH_BYTES)
        val iv = data.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(IV_LENGTH_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Works with the screen off; the threat model is storage, not user presence.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "telerelay_settings_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
