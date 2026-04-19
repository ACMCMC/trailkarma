package fyi.acmc.trailkarma.wallet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import fyi.acmc.trailkarma.util.Base58
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

data class ManagedWallet(
    val publicKeyBase58: String,
    val publicKeyBytes: ByteArray
)

class WalletManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "wallet_store",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun ensureWallet(userId: String): ManagedWallet {
        val publicKeyEncoded = prefs.getString("${userId}_public_x509", null)
        val privateKeyEncoded = prefs.getString("${userId}_private_pkcs8", null)
        if (publicKeyEncoded != null && privateKeyEncoded != null) {
            val publicBytes = Base64.getDecoder().decode(publicKeyEncoded).takeLast(32).toByteArray()
            return ManagedWallet(Base58.encode(publicBytes), publicBytes)
        }

        val generator = KeyPairGenerator.getInstance("Ed25519")
        val pair = generator.generateKeyPair()
        prefs.edit()
            .putString("${userId}_public_x509", Base64.getEncoder().encodeToString(pair.public.encoded))
            .putString("${userId}_private_pkcs8", Base64.getEncoder().encodeToString(pair.private.encoded))
            .apply()

        val publicBytes = pair.public.encoded.takeLast(32).toByteArray()
        return ManagedWallet(Base58.encode(publicBytes), publicBytes)
    }

    fun sign(userId: String, payload: ByteArray): ByteArray {
        val privateKeyEncoded = prefs.getString("${userId}_private_pkcs8", null)
            ?: error("No private key stored for user $userId")
        val privateKey = KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyEncoded)))
        return Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(payload)
            sign()
        }
    }
}
