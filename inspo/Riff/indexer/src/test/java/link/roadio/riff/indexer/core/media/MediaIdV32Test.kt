package link.roadio.riff.indexer.core.media

import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.IndexingException
import link.roadio.riff.indexer.core.model.SourceMedia
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MediaIdV32Test {
    @Test
    fun publishedConstantsAreOwnedByV32() {
        assertEquals("MDIA-ID-V3.2", MediaIdV32Generator.IDENTITY_VERSION)
        assertEquals(131_072L, MediaIdV32Generator.UNIQUE_PAYLOAD_TARGET)
    }

    @Test
    fun formsIndependentHeadAndTailComponents() {
        val packets = uniquePackets(600)
        val fixture = fixture(FakeAccessInput(packets, secondTimes(packets.size), durationUs = 600_000_000L, channelCount = 2))

        val result = fixture.generator.generateTail(SOURCE, NeverCancelled)

        val sample = fixture.samples.single()
        assertEquals(result, sample.digest)
        assertEquals("MDIA-ID-V3.2", sample.identityVersion)
        assertEquals("ACCESS_UNITS", sample.mode)
        assertEquals("HEAD_TAIL", sample.terminationReason)
        assertEquals(256, sample.selectedOccurrences)
        assertEquals(256L * 1024L, sample.selectedPayloadBytes)

        val framed =
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()
        assertEquals("MDIA-ID-V3.2-TAIL-ID", framed.copyOfRange(0, 20).decodeToString())
        assertArrayEquals(byteArrayOf(0, 1, 0, 0, 0, 2, 0), framed.copyOfRange(20, 27))
        assertEquals(128L, ByteBuffer.wrap(framed, 27, 8).long)
        assertEquals(450_000_000L, ByteBuffer.wrap(framed, 43, 8).long)
        assertEquals(128L, ByteBuffer.wrap(framed, 51, 8).long)
        assertEquals(67, framed.size)
    }

    @Test
    fun floorsThreeQuarterStartAndDiscardsPreviousSyncPreroll() {
        val packets = uniquePackets(700)
        val times = List(packets.size) { it * 1_000L }
        val fixture = fixture(FakeAccessInput(packets, times, durationUs = 600_003L, channelCount = null))

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        val framed =
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()
        assertEquals("HEAD_TAIL", fixture.samples.single().terminationReason)
        assertEquals(256, fixture.samples.single().selectedOccurrences)
        assertEquals(450_002L, ByteBuffer.wrap(framed, 39, 8).long)
        assertEquals(128L, ByteBuffer.wrap(framed, 47, 8).long)
    }

    @Test
    fun normalizesNonzeroSourceTimelineBeforeTailSelection() {
        val packets = uniquePackets(600)
        val zero = fixture(FakeAccessInput(packets, secondTimes(600), durationUs = 600_000_000L, channelCount = 2))
        val shiftedTimes = secondTimes(600).map { it + 5_000_000L }
        val shifted = fixture(FakeAccessInput(packets, shiftedTimes, durationUs = 605_000_000L, channelCount = 2))

        val zeroId = zero.generator.generateTail(SOURCE, NeverCancelled)
        val shiftedId = shifted.generator.generateTail(SOURCE, NeverCancelled)

        assertEquals(zeroId, shiftedId)
        assertEquals("HEAD_TAIL", shifted.samples.single().terminationReason)
    }

    @Test
    fun overlapFallsBackToCompleteScan() {
        val packets = uniquePackets(260)
        val fixture = fixture(FakeAccessInput(packets, secondTimes(packets.size), durationUs = 160_000_000L, channelCount = 2))

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        assertEquals(listOf(MediaIdV32FullScanReason.OVERLAP), fixture.reasons)
        assertEquals("FULL", fixture.samples.single().terminationReason)
        assertEquals(260, fixture.samples.single().selectedOccurrences)
        assertEquals(
            0x01,
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()[26]
                .toInt(),
        )
    }

    @Test
    fun missingTimingUsesCompleteScan() {
        val packets = uniquePackets(140)
        val fixture = fixture(FakeAccessInput(packets, secondTimes(packets.size), durationUs = null, channelCount = 1))

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        assertEquals(listOf(MediaIdV32FullScanReason.TIMING), fixture.reasons)
        assertEquals("FULL", fixture.samples.single().terminationReason)
        assertEquals(140, fixture.samples.single().selectedOccurrences)
    }

    @Test
    fun shortStreamBecomesFullWithoutASecondScan() {
        val fixture =
            fixture(
                FakeAccessInput(
                    listOf(byteArrayOf(1, 2), byteArrayOf(3, 4, 5)),
                    listOf(0L, 1_000_000L),
                    durationUs = 2_000_000L,
                    channelCount = 2,
                ),
            )

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        assertTrue(fixture.reasons.isEmpty())
        assertEquals("FULL", fixture.samples.single().terminationReason)
        assertEquals(2, fixture.samples.single().selectedOccurrences)
        assertEquals(5L, fixture.samples.single().selectedPayloadBytes)
    }

    @Test
    fun unavailableSeekFallsBackToCompleteScan() {
        val packets = uniquePackets(400)
        val fixture =
            fixture(
                FakeAccessInput(
                    packets,
                    secondTimes(packets.size),
                    durationUs = 400_000_000L,
                    channelCount = 2,
                    seekUnavailable = true,
                ),
            )

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        assertEquals(listOf(MediaIdV32FullScanReason.SEEK), fixture.reasons)
        assertEquals("FULL", fixture.samples.single().terminationReason)
    }

    @Test
    fun nonconsecutiveRepeatsDoNotAdvanceUniqueByteThreshold() {
        val packets = uniquePackets(900).toMutableList()
        for (index in 739 until 803) packets[index] = packets[index - 64]
        val fixture =
            fixture(
                FakeAccessInput(
                    packets,
                    secondTimes(packets.size),
                    durationUs = 900_000_000L,
                    channelCount = 2,
                ),
            )

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        val framed =
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()
        assertEquals("HEAD_TAIL", fixture.samples.single().terminationReason)
        assertEquals(128L, ByteBuffer.wrap(framed, 27, 8).long)
        assertEquals(192L, ByteBuffer.wrap(framed, 51, 8).long)
    }

    @Test
    fun insufficientBoundedTailFallsBackToCompleteScan() {
        val packets = uniquePackets(1_000).toMutableList()
        for (index in 128 until packets.size) packets[index] = ByteArray(1024) { 9 }
        val fixture =
            fixture(
                FakeAccessInput(
                    packets,
                    secondTimes(packets.size),
                    durationUs = 1_000_000_000L,
                    channelCount = 2,
                ),
            )

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        assertEquals(listOf(MediaIdV32FullScanReason.TAIL), fixture.reasons)
        assertEquals("FULL", fixture.samples.single().terminationReason)
        assertEquals(1_000, fixture.samples.single().selectedOccurrences)
    }

    @Test
    fun unusableTailTimingFallsBackToCompleteScan() {
        val packets = uniquePackets(400)
        val times = secondTimes(packets.size).toMutableList().apply { this[350] = this[349] - 1L }
        val fixture = fixture(FakeAccessInput(packets, times, durationUs = 400_000_000L, channelCount = 2))

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        assertEquals(listOf(MediaIdV32FullScanReason.TIMING), fixture.reasons)
        assertEquals("FULL", fixture.samples.single().terminationReason)
    }

    @Test
    fun cancellationNeverPublishesAPartialIdentity() {
        val fixture =
            fixture(
                FakeAccessInput(
                    uniquePackets(400),
                    secondTimes(400),
                    durationUs = 400_000_000L,
                    channelCount = 2,
                ),
            )

        val error = runCatching { fixture.generator.generateTail(SOURCE, CancelsAfter(16)) }.exceptionOrNull()

        assertEquals("cancelled", error?.message)
        assertEquals(false, fixture.samples.single().success)
        assertEquals(null, fixture.samples.single().digest)
    }

    @Test
    fun accessUnitReadFailureNeverPublishesAPartialIdentity() {
        val fixture =
            fixture(
                FakeAccessInput(
                    uniquePackets(400),
                    secondTimes(400),
                    durationUs = 400_000_000L,
                    channelCount = 2,
                    failReadAt = 15,
                ),
            )

        val error = runCatching { fixture.generator.generateTail(SOURCE, NeverCancelled) }.exceptionOrNull()

        assertEquals("fixture read failed", error?.message)
        assertEquals(false, fixture.samples.single().success)
        assertEquals(null, fixture.samples.single().digest)
    }

    @Test
    fun waveTailUsesV32FramingAndCompletePayload() {
        val data = ByteArray(131_082) { (it and 0xff).toByte() }
        val fixture = fixture(FakeRandomInput(wave(channels = 3, blockAlign = 6, data = data, metadata = byteArrayOf(9, 8, 7))))

        fixture.generator.generateTail(SOURCE, NeverCancelled)

        val sample = fixture.samples.single()
        assertEquals("WAVE_RAW", sample.mode)
        assertEquals("FULL", sample.terminationReason)
        assertEquals(131_082L, sample.selectedPayloadBytes)
        val framed =
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()
        assertEquals("MDIA-ID-V3.2-TAIL-ID", framed.copyOfRange(0, 20).decodeToString())
        assertEquals(1, framed[20].toInt())
        assertEquals(131_082L, ByteBuffer.wrap(framed, 26, 8).long)
        assertArrayEquals(data, framed.copyOfRange(34, 34 + data.size))
    }

    @Test
    fun rejectsEmptyAccessUnitStream() {
        val fixture = fixture(FakeAccessInput(emptyList(), emptyList(), durationUs = 1L, channelCount = 2))

        val error = runCatching { fixture.generator.generateTail(SOURCE, NeverCancelled) }.exceptionOrNull()

        assertEquals("NO_AUDIO_PACKETS", (error as IndexingException).code)
        assertEquals(false, fixture.samples.single().success)
    }

    @Test
    fun rawWaveSelectionDefersCompressedInputToAdapter() {
        val factory = RecordingFactory()
        val generator =
            object : MediaIdV32Generator(factory, "test") {
                override fun openInput(source: SourceMedia): MediaIdV32Input = error("Selection only")

                fun raw(bytes: ByteArray): Boolean = supportsWaveRaw(FakeRandomInput(bytes))
            }
        val pcm = wave(channels = 2, blockAlign = 4, data = ByteArray(24))
        assertEquals(true, generator.raw(pcm))
        val compressed = pcm.copyOf().apply { this[20] = 17 }
        assertEquals(false, generator.raw(compressed))
    }

    @Test
    fun observesHighWithoutTailReadsAndCountsRepeatedPayload() {
        val packets = uniquePackets(150).toMutableList().apply { add(1, first()) }
        val fixture = fixture(FakeAccessInput(packets, secondTimes(packets.size), null, 2))

        val result = fixture.generator.generateHigh(SOURCE, NeverCancelled)

        val sample = fixture.samples.single()
        assertTrue(sample.success)
        assertEquals(result, sample.digest)
        assertEquals("HEAD", sample.terminationReason)
        assertEquals("ACCESS_UNITS", sample.mode)
        assertEquals(129, sample.selectedOccurrences)
        assertEquals(129L * 1024L, sample.selectedPayloadBytes)
        assertEquals(setOf("input.open", "head.hash"), sample.phasesElapsedNanos.keys)
        assertTrue(fixture.reasons.isEmpty())
        val framed =
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()
        assertEquals("MDIA-ID-V3.2-HIGH-ID", framed.copyOfRange(0, 20).decodeToString())
        assertEquals(129L, ByteBuffer.wrap(framed, 26, 8).long)
    }

    @Test
    fun observesBlockAlignedWaveHigh() {
        val data = ByteArray(131_082) { (it and 0xff).toByte() }
        val fixture = fixture(FakeRandomInput(wave(channels = 3, blockAlign = 6, data = data)))

        val result = fixture.generator.generateHigh(SOURCE, NeverCancelled)

        val sample = fixture.samples.single()
        assertEquals(result, sample.digest)
        assertEquals("WAVE_RAW", sample.mode)
        assertEquals("HEAD", sample.terminationReason)
        assertEquals(131_076L, sample.selectedPayloadBytes)
        val framed =
            fixture.factory.sessions
                .last()
                .framed
                .toByteArray()
        assertArrayEquals(data.copyOf(131_076), framed.copyOfRange(34, framed.size))
    }

    @Test
    fun observesHighCancellationAndReadFailureWithoutPublishingPartialIdentity() {
        for (failRead in listOf(false, true)) {
            val fixture = fixture(FakeAccessInput(uniquePackets(150), secondTimes(150), null, 2, failReadAt = if (failRead) 15 else null))
            val error =
                runCatching {
                    fixture.generator.generateHigh(SOURCE, if (failRead) NeverCancelled else CancelsAfter(16))
                }.exceptionOrNull()
            assertEquals(if (failRead) "fixture read failed" else "cancelled", error?.message)
            assertEquals(false, fixture.samples.single().success)
            assertEquals(null, fixture.samples.single().digest)
        }
    }

    private fun fixture(input: MediaIdV32Input): Fixture {
        val factory = RecordingFactory()
        val samples = mutableListOf<MediaIdBenchmarkSample>()
        val reasons = mutableListOf<MediaIdV32FullScanReason>()
        val generator =
            object : MediaIdV32Generator(
                xxh3Factory = factory,
                platform = "test",
                benchmarkObserver = MediaIdBenchmarkObserver(samples::add),
                onFullScan = reasons::add,
                nanoTime = { 100L },
            ) {
                override fun openInput(source: SourceMedia): MediaIdV32Input = input
            }
        return Fixture(generator, factory, samples, reasons)
    }

    private class RecordingFactory : MediaIdXxh3Factory {
        val sessions = mutableListOf<RecordingSession>()

        override fun open(): MediaIdXxh3Session = RecordingSession().also(sessions::add)
    }

    private class RecordingSession : MediaIdXxh3Session {
        val framed = ByteArrayOutputStream()

        override fun update(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) = framed.write(bytes, offset, length)

        override fun hash(bytes: ByteArray): Long =
            bytes.fold(FNV64_OFFSET) { hash, byte ->
                (hash xor (byte.toLong() and 0xffL)) * FNV64_PRIME
            }

        override fun digest(): Long = framed.toByteArray().contentHashCode().toLong()

        override fun close() = Unit
    }

    private class FakeAccessInput(
        private val packets: List<ByteArray>,
        private val timesUs: List<Long>,
        override val durationUs: Long?,
        override val channelCount: Int?,
        private val seekUnavailable: Boolean = false,
        private val failReadAt: Int? = null,
    ) : MediaIdV32AccessUnitInput {
        init {
            require(packets.size == timesUs.size)
        }

        override fun openCursor(sourceTimeUs: Long?): MediaIdV32AccessUnitCursor {
            if (sourceTimeUs != null && seekUnavailable) {
                throw IndexingException("SEEK_UNAVAILABLE", "test seek failure")
            }
            val start =
                sourceTimeUs?.let { target ->
                    val previous = timesUs.indexOfLast { it <= target }
                    (previous - 1).coerceAtLeast(0)
                } ?: 0
            return FakeAccessCursor(packets, timesUs, start, failReadAt)
        }

        override fun close() = Unit
    }

    private class FakeAccessCursor(
        private val packets: List<ByteArray>,
        private val timesUs: List<Long>,
        start: Int,
        private val failReadAt: Int?,
    ) : MediaIdV32AccessUnitCursor {
        private var index = start - 1

        override fun advance(): Boolean {
            index += 1
            return index < packets.size
        }

        override val presentationTimeUs: Long
            get() = timesUs[index]

        override val encodedSize: Int
            get() = packets[index].size

        override fun readEncodedBytes(): ByteArray {
            if (index == failReadAt) error("fixture read failed")
            return packets[index].copyOf()
        }

        override fun close() = Unit
    }

    private class FakeRandomInput(
        private val bytes: ByteArray,
    ) : MediaIdV32RandomAccessInput {
        override val size: Long = bytes.size.toLong()

        override fun readFully(
            offset: Long,
            bytes: ByteArray,
            destinationOffset: Int,
            length: Int,
        ) {
            this.bytes.copyInto(bytes, destinationOffset, offset.toInt(), offset.toInt() + length)
        }

        override fun close() = Unit
    }

    private data class Fixture(
        val generator: MediaIdV32Generator,
        val factory: RecordingFactory,
        val samples: MutableList<MediaIdBenchmarkSample>,
        val reasons: MutableList<MediaIdV32FullScanReason>,
    )

    private object NeverCancelled : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }

    private class CancelsAfter(
        private val permittedChecks: Int,
    ) : CancellationSignal {
        private var checks = 0
        override val cancelled: Boolean
            get() = checks > permittedChecks

        override fun ensureAvailable() {
            checks += 1
            if (cancelled) error("cancelled")
        }
    }

    companion object {
        private const val FNV64_OFFSET = -0x340d631b7bdddcdbL
        private const val FNV64_PRIME = 0x100000001b3L
        private val SOURCE = SourceMedia("memory:test", "test.media")

        private fun uniquePackets(count: Int): List<ByteArray> =
            List(count) { index ->
                ByteBuffer
                    .allocate(1024)
                    .putInt(index)
                    .array()
                    .copyOf(1024)
            }

        private fun secondTimes(count: Int): List<Long> = List(count) { it * 1_000_000L }

        private fun wave(
            channels: Int,
            blockAlign: Int,
            data: ByteArray,
            metadata: ByteArray? = null,
        ): ByteArray {
            val chunks = ByteArrayOutputStream()
            chunks.write("fmt ".encodeToByteArray())
            chunks.write(le32(16))
            chunks.write(le16(1))
            chunks.write(le16(channels))
            chunks.write(le32(48_000))
            chunks.write(le32(48_000 * blockAlign))
            chunks.write(le16(blockAlign))
            chunks.write(le16(16))
            metadata?.let {
                chunks.write("JUNK".encodeToByteArray())
                chunks.write(le32(it.size))
                chunks.write(it)
                if (it.size % 2 != 0) chunks.write(0)
            }
            chunks.write("data".encodeToByteArray())
            chunks.write(le32(data.size))
            chunks.write(data)
            if (data.size % 2 != 0) chunks.write(0)
            val body = chunks.toByteArray()
            return ByteArrayOutputStream()
                .apply {
                    write("RIFF".encodeToByteArray())
                    write(le32(body.size + 4))
                    write("WAVE".encodeToByteArray())
                    write(body)
                }.toByteArray()
        }

        private fun le16(value: Int): ByteArray =
            ByteBuffer
                .allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(value.toShort())
                .array()

        private fun le32(value: Int): ByteArray =
            ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array()
    }
}
