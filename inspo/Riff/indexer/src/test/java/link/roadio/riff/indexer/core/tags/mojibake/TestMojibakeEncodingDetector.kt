package link.roadio.cuegrain.repair

import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal class TestMojibakeEncodingDetector(
    private val detection: ((ByteArray) -> MojibakeEncodingDetection)? = null,
) : MojibakeEncodingDetector {
    val inputs = mutableListOf<ByteArray>()

    override val revision: String = "TEST-CED"

    override fun detect(input: ByteArray): MojibakeEncodingDetection {
        inputs += input.copyOf()
        return detection?.invoke(input) ?: classify(input)
    }

    private fun classify(input: ByteArray): MojibakeEncodingDetection {
        val isUtf8 =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(input))
                input.any { byte -> byte.toInt() and 0x80 != 0 }
            } catch (_: CharacterCodingException) {
                false
            }
        return MojibakeEncodingDetection(
            encoding = if (isUtf8) MojibakeEncoding.UTF8 else MojibakeEncoding.WINDOWS_1251,
            isReliable = input.any { byte -> byte.toInt() and 0x80 != 0 },
            bytesConsumed = input.size,
        )
    }
}
