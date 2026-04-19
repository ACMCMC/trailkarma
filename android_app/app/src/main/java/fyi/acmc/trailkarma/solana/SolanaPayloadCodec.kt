package fyi.acmc.trailkarma.solana

import fyi.acmc.trailkarma.util.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SolanaPayloadCodec {
    fun relayIntentMessage(
        jobIdHex: String,
        senderWalletBase58: String,
        destinationHashHex: String,
        payloadHashHex: String,
        expiryTs: Long,
        rewardAmount: Int,
        nonce: Long
    ): ByteArray {
        val buffer = ByteBuffer.allocate(154).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(1)
        buffer.put(1)
        buffer.put(hex(jobIdHex))
        buffer.put(Base58.decode(senderWalletBase58))
        buffer.put(hex(destinationHashHex))
        buffer.put(hex(payloadHashHex))
        buffer.putLong(expiryTs)
        buffer.putLong(rewardAmount.toLong())
        buffer.putLong(nonce)
        return buffer.array()
    }

    fun tipIntentMessage(
        tipIdHex: String,
        senderWalletBase58: String,
        recipientWalletBase58: String,
        amount: Int,
        nonce: Long
    ): ByteArray {
        val buffer = ByteBuffer.allocate(114).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(2)
        buffer.put(1)
        buffer.put(hex(tipIdHex))
        buffer.put(Base58.decode(senderWalletBase58))
        buffer.put(Base58.decode(recipientWalletBase58))
        buffer.putLong(amount.toLong())
        buffer.putLong(nonce)
        return buffer.array()
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
