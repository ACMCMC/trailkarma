package fyi.acmc.trailkarma.util

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implements a simple ECIES-like hybrid encryption for relay payloads.
 * 1. Generates an ephemeral X25519 key pair.
 * 2. Performs ECDH with the backend's public key to get a shared secret.
 * 3. Encrypts the payload using AES-256-GCM.
 * 4. Packs [EphemeralPublicKey(32), IV(12), Ciphertext+Tag] into a Base64 string.
 */
object EncryptionUtil {
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16

    fun encryptForBackend(plainText: String, backendPublicKeyHex: String): String {
        // 1. Generate Ephemeral Key Pair
        val kpg = KeyPairGenerator.getInstance("X25519")
        val ephemeralKeyPair = kpg.generateKeyPair()
        val ephemeralPubKey = ephemeralKeyPair.public.encoded // This is SPKI, we need raw 32 bytes

        // 2. Load Backend Public Key
        val backendPubKeyBytes = hexToBytes(backendPublicKeyHex)
        val backendKeySpec = X509EncodedKeySpec(buildSpkiHeader() + backendPubKeyBytes)
        val kf = KeyFactory.getInstance("X25519")
        val backendPubKey = kf.generatePublic(backendKeySpec)

        // 3. Perform ECDH
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(ephemeralKeyPair.private)
        ka.doPhase(backendPubKey, true)
        val sharedSecret = ka.generateSecret()

        // 4. Encrypt with AES-GCM
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), spec)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 5. Pack: [EphemeralPubKey(32), IV(12), Ciphertext]
        // Extract raw 32 bytes from SPKI (it's at the end)
        val rawEphemeralPubKey = ephemeralPubKey.takeLast(32).toByteArray()
        val packed = ByteArray(32 + GCM_IV_LENGTH + cipherText.size)
        System.arraycopy(rawEphemeralPubKey, 0, packed, 0, 32)
        System.arraycopy(iv, 0, packed, 32, GCM_IV_LENGTH)
        System.arraycopy(cipherText, 0, packed, 32 + GCM_IV_LENGTH, cipherText.size)

        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun buildSpkiHeader(): ByteArray {
        // SPKI header for X25519: 302a300506032b656e032100 in hex
        return hexToBytes("302a300506032b656e032100")
    }
}
