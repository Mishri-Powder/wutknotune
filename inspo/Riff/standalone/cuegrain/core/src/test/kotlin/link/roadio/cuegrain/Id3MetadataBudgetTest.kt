// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import org.junit.Assert.*
import org.junit.Test

class Id3MetadataBudgetTest {
    private fun header(
        version: Int = 4,
        size: Int = 0,
        flags: Int = 0,
    ): ByteArray =
        byteArrayOf(
            'I'.code.toByte(),
            'D'.code.toByte(),
            '3'.code.toByte(),
            version.toByte(),
            0,
            flags.toByte(),
            ((size ushr 21) and 127).toByte(),
            ((size ushr 14) and 127).toByte(),
            ((size ushr 7) and 127).toByte(),
            (size and 127).toByte(),
        )

    @Test fun rejectsHugeTagBeforeBodyAllocation() {
        assertThrows(IllegalArgumentException::class.java) {
            Id3MetadataBudget.bodySize(header(size = 0x0fffffff))
        }
    }

    @Test fun rejectsInvalidSynchsafeAndTruncatedSource() {
        val invalid = header().also { it[6] = 0xff.toByte() }
        assertThrows(IllegalArgumentException::class.java) { Id3MetadataBudget.bodySize(invalid) }
        assertThrows(IllegalArgumentException::class.java) {
            Id3MetadataBudget.bodySize(header(size = 100), remainingSourceBytes = 109)
        }
    }

    @Test fun acceptsBoundedTagAndCountsFooter() {
        assertEquals(100, Id3MetadataBudget.bodySize(header(size = 100), remainingSourceBytes = 110))
        assertThrows(IllegalArgumentException::class.java) {
            Id3MetadataBudget.bodySize(header(size = 100, flags = 0x10), remainingSourceBytes = 110)
        }
        assertEquals(100, Id3MetadataBudget.bodySize(header(size = 100, flags = 0x10), remainingSourceBytes = 120))
    }
}
