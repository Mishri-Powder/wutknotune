// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.ced

import link.roadio.cuegrain.*
import org.junit.Assert.*
import org.junit.Test

class CedEncodingDetectorTest {
    @Test fun nativeBackendDecodesRussianLegacyCue() {
        val text = "PERFORMER \"Ансамбль народной музыки\"\nTITLE \"Любимые песни родного края\"\nFILE \"album.flac\" WAVE\nTRACK 01 AUDIO\nTITLE \"Прекрасная музыка летнего вечера\"\nINDEX 01 00:00:00"
        val detector = CedEncodingDetector()
        val input = text.toByteArray(charset("windows-1251"))
        val d = detector.detect(input)
        assertTrue(d.toString(), d.isReliable)
        assertEquals("windows-1251", d.charset?.lowercase())
        val result = Cuegrain(ParseOptions(detector = detector)).read(ByteArrayInput(input), "cue")
        assertEquals(result.diagnostics.toString(), "ok", result.status)
        assertEquals(
            "Ансамбль народной музыки",
            result.sources
                .single()
                .tags
                .first()
                .value,
        )
    }

    @Test fun oversizeInputIsRejectedBeforeNativeCall() {
        try {
            CedEncodingDetector().detect(ByteArray(65537))
            fail("Expected bound")
        } catch (_: IllegalArgumentException) {
        }
    }
}
