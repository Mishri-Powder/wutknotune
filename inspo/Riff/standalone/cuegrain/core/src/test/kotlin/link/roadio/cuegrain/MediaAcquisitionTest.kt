package link.roadio.cuegrain

import link.roadio.cuegrain.identity.MediaIdentity
import link.roadio.cuegrain.identity.fragmentIdentityComponent
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class MediaAcquisitionTest {
    @Test fun rangeCacheIsBoundedAndDoesNotExposeStoredArrays() {
        val reads = mutableListOf<Pair<Long, Int>>()
        val source =
            object : MediaInput {
                override val id = "test"
                override val length = 32L

                override fun read(
                    offset: Long,
                    count: Int,
                ): ByteArray {
                    reads += offset to count
                    return ByteArray(count) { (offset + it).toByte() }
                }
            }
        val cache = CachedMediaInput(source, 8)
        cache.read(0, 8)[0] = 99
        assertArrayEquals(byteArrayOf(1, 2), cache.read(1, 2))
        cache.read(8, 8)
        cache.read(0, 8)
        assertEquals(listOf(0L to 8, 8L to 8, 0L to 8), reads)
    }

    @Test fun pairedAcquisitionSharesTheParserInputWithoutChangingOrdinaryRead() {
        var reads = 0
        val source =
            object : MediaInput {
                override val id = "unknown"
                override val length = 12L

                override fun read(
                    offset: Long,
                    count: Int,
                ): ByteArray {
                    reads++
                    return ByteArray(count)
                }
            }
        var identityCalls = 0
        Cuegrain().read(source)
        assertEquals(0, identityCalls)
        val acquired =
            Cuegrain().acquire(source) { shared ->
                identityCalls++
                val before = reads
                shared.read(0, 8)
                assertEquals(before, reads)
                MediaIdentity(42)
            }
        assertEquals(42L, acquired.media_id.high)
        assertEquals(1, identityCalls)
    }

    @Test fun fragmentFrameRetainsNullableBoundsAndExistingDomains() {
        var bytes = byteArrayOf()
        val high =
            fragmentIdentityComponent(17, null, 200, false) {
                bytes = it
                91
            }
        assertEquals(91L, high)
        val frame = ByteBuffer.wrap(bytes)
        assertEquals(27, bytes.size)
        assertEquals(0x46, frame.get().toInt())
        assertEquals(17L, frame.long)
        assertEquals(0, frame.get().toInt())
        assertEquals(0L, frame.long)
        assertEquals(1, frame.get().toInt())
        assertEquals(200L, frame.long)
        fragmentIdentityComponent(0, 0, null, true) {
            assertEquals(0x4c, it[0].toInt())
            0
        }
    }
}
