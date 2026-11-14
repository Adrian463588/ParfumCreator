package com.parfumecreator.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted payload containing initial vector (IV) and AES-GCM ciphertext with authentication tag.
 */
data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    fun toBase64String(): String {
        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val dataBase64 = Base64.getEncoder().encodeToString(ciphertext)
        return "$ivBase64:$dataBase64"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPayload
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    companion object {
        fun fromBase64String(encoded: String): EncryptedPayload? {
            val parts = encoded.split(":")
            if (parts.size != 2) return null
            return try {
                val iv = Base64.getDecoder().decode(parts[0])
                val ciphertext = Base64.getDecoder().decode(parts[1])
                EncryptedPayload(iv, ciphertext)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Cryptographic service leveraging hardware-backed Android Keystore for AES-256 GCM encryption.
 * Eliminates static hardcoded encryption keys in APK binaries.
 */
class AndroidKeyStoreCrypto(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    companion object {
        const val DEFAULT_KEY_ALIAS = "parfume_creator_master_key"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }

    /**
     * Retrieves existing 256-bit AES key from Android Keystore or generates a new one.
     */
    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Expected SecretKeyEntry for alias $keyAlias")
        return entry.secretKey
    }

    /**
     * Encrypts plaintext bytes using AES-GCM with a dynamically generated IV.
     */
    fun encrypt(plaintext: ByteArray, secretKey: SecretKey = getOrCreateKey()): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(iv, ciphertext)
    }

    /**
     * Decrypts AES-GCM payload validating integrity tag.
     */
    fun decrypt(payload: EncryptedPayload, secretKey: SecretKey = getOrCreateKey()): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(payload.ciphertext)
    }

    /**
     * Encrypts a string into a base64-encoded composite representation ("iv:ciphertext").
     */
    fun encryptString(plaintext: String, secretKey: SecretKey = getOrCreateKey()): String {
        val payload = encrypt(plaintext.toByteArray(Charsets.UTF_8), secretKey)
        return payload.toBase64String()
    }

    /**
     * Decrypts a base64-encoded composite string. Returns null if invalid or tampered.
     */
    fun decryptString(encodedBase64: String, secretKey: SecretKey = getOrCreateKey()): String? {
        val payload = EncryptedPayload.fromBase64String(encodedBase64) ?: return null
        return try {
            val decryptedBytes = decrypt(payload, secretKey)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
