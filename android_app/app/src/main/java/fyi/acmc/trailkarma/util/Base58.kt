package fyi.acmc.trailkarma.util

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { index, c -> this[c.code] = index }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        val copy = input.copyOf()
        val zeros = copy.indexOfFirst { it.toInt() != 0 }.let { if (it == -1) copy.size else it }
        val encoded = CharArray(copy.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros

        while (inputStart < copy.size) {
            val mod = divmod58(copy, inputStart)
            if (copy[inputStart].toInt() == 0) inputStart++
            encoded[--outputStart] = ALPHABET[mod]
        }

        // Drop Base58 zeroes introduced by the division loop, then prepend
        // only the original zero-prefix bytes as leading '1' characters.
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) {
            outputStart++
        }

        repeat(zeros) {
            encoded[--outputStart] = ALPHABET[0]
        }

        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val input58 = ByteArray(input.length)
        input.forEachIndexed { i, c ->
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid Base58 character: $c" }
            input58[i] = digit.toByte()
        }

        var zeros = 0
        while (zeros < input58.size && input58[zeros].toInt() == 0) zeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input58.size) {
            val mod = divmod256(input58, inputStart)
            if (input58[inputStart].toInt() == 0) inputStart++
            decoded[--outputStart] = mod.toByte()
        }

        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        return ByteArray(zeros + decoded.size - outputStart).also {
            java.util.Arrays.fill(it, 0, zeros, 0.toByte())
            System.arraycopy(decoded, outputStart, it, zeros, decoded.size - outputStart)
        }
    }

    private fun divmod58(number: ByteArray, startAt: Int): Int {
        var remainder = 0
        for (i in startAt until number.size) {
            val digit256 = number[i].toInt() and 0xFF
            val temp = remainder * 256 + digit256
            number[i] = (temp / 58).toByte()
            remainder = temp % 58
        }
        return remainder
    }

    private fun divmod256(number58: ByteArray, startAt: Int): Int {
        var remainder = 0
        for (i in startAt until number58.size) {
            val digit58 = number58[i].toInt() and 0xFF
            val temp = remainder * 58 + digit58
            number58[i] = (temp / 256).toByte()
            remainder = temp % 256
        }
        return remainder
    }
}
