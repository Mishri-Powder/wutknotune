// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.ByteArrayInput
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKindObservationTest {
    @Test fun appleAudiobookKindSurvivesCuegrainToCuetreeObservation() {
        fun atom(
            name: String,
            bytes: ByteArray,
        ): ByteArray {
            val size = bytes.size + 8
            return ByteArray(4) { i -> (size ushr (24 - 8 * i)).toByte() } + name.toByteArray() + bytes
        }
        val bytes =
            atom("ftyp", "M4A ".toByteArray()) +
                atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", atom("stik", atom("data", ByteArray(8) + byteArrayOf(2)))))))
        val observation = CuegrainMetadataReader().read(ByteArrayInput(bytes, "book.m4b"), "m4b", Cancellation())
        assertEquals(listOf("2"), observation.attributes["mediaKind"])
    }
}
