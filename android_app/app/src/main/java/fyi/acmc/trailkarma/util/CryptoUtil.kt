package fyi.acmc.trailkarma.util

import java.security.MessageDigest

object CryptoUtil {
    fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(input: String): String = sha256(input).joinToString("") { "%02x".format(it) }
}
