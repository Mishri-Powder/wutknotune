package link.roadio.riff.indexer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DecoderHintV1Test {
    @Test
    fun framesCodecParametersBeforeHashing() {
        var framed = byteArrayOf()
        val hint =
            DecoderHintV1 { bytes ->
                framed = bytes
                -7L
            }.form(
                sampleMimeType = "audio/aac",
                sampleRate = 48_000,
                channelCount = 2,
                initializationData = listOf(byteArrayOf(0x12, 0x10), byteArrayOf(0x7f)),
                isAdts = true,
            )

        assertEquals(-7L, hint)
        assertEquals(
            "524946462d4445434f4445522d48494e542d5631" +
                "00000009617564696f2f616163" +
                "0000bb80000000020100000002" +
                "000000021210000000017f",
            framed.toHex(),
        )
    }

    @Test
    fun everyCohortParameterChangesTheHashInput() {
        val generator = DecoderHintV1(::stableHash)
        val baseline = generator.form("audio/aac", 48_000, 2, listOf(byteArrayOf(0x12, 0x10)), false)
        assertEquals(baseline, generator.form("audio/aac", 48_000, 2, listOf(byteArrayOf(0x12, 0x10)), false))
        val variants =
            listOf(
                generator.form("audio/opus", 48_000, 2, listOf(byteArrayOf(0x12, 0x10)), false),
                generator.form("audio/aac", 44_100, 2, listOf(byteArrayOf(0x12, 0x10)), false),
                generator.form("audio/aac", 48_000, 1, listOf(byteArrayOf(0x12, 0x10)), false),
                generator.form("audio/aac", 48_000, 2, listOf(byteArrayOf(0x12, 0x11)), false),
                generator.form("audio/aac", 48_000, 2, listOf(byteArrayOf(0x12, 0x10)), true),
            )

        assertFalse(variants.contains(baseline))
        assertEquals(variants.size, variants.toSet().size)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun stableHash(bytes: ByteArray): Long =
        bytes
            .fold(0xcbf29ce484222325uL) { value, byte ->
                (value xor byte.toUByte().toULong()) * 0x100000001b3uL
            }.toLong()
}
