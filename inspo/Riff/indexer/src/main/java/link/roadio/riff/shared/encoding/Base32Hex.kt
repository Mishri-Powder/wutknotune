package link.roadio.riff.shared.encoding

/** Unpadded RFC 4648 section 7 Base32hex. */
object Base32Hex {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV"

    fun encode(bytes: ByteArray): String {
        val encoded = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitCount = 0
        bytes.forEach { byte ->
            buffer = (buffer shl Byte.SIZE_BITS) or byte.toUByte().toInt()
            bitCount += Byte.SIZE_BITS
            while (bitCount >= 5) {
                bitCount -= 5
                encoded.append(ALPHABET[(buffer ushr bitCount) and 0x1f])
            }
        }
        if (bitCount > 0) encoded.append(ALPHABET[(buffer shl (5 - bitCount)) and 0x1f])
        return encoded.toString()
    }

    fun decode(encoded: String): ByteArray {
        require(encoded.length % 8 in VALID_LENGTH_REMAINDERS) {
            "Invalid unpadded Base32hex length"
        }
        val bytes = ArrayList<Byte>(encoded.length * 5 / Byte.SIZE_BITS)
        var buffer = 0
        var bitCount = 0
        encoded.forEach { character ->
            val value = ALPHABET.indexOf(character)
            require(value >= 0) { "Invalid Base32hex character: $character" }
            buffer = (buffer shl 5) or value
            bitCount += 5
            while (bitCount >= Byte.SIZE_BITS) {
                bitCount -= Byte.SIZE_BITS
                bytes += ((buffer ushr bitCount) and 0xff).toByte()
            }
        }
        require(bitCount == 0 || (buffer and ((1 shl bitCount) - 1)) == 0) {
            "Base32hex value has non-zero trailing bits"
        }
        return bytes.toByteArray()
    }

    private val VALID_LENGTH_REMAINDERS = setOf(0, 2, 4, 5, 7)
}
