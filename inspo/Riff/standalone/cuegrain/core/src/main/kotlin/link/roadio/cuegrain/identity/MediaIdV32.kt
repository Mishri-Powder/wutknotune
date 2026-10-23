// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.identity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

interface MediaIdXxh3Session : AutoCloseable {
    fun update(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    )

    fun hash(bytes: ByteArray): Long

    fun digest(): Long
}

fun interface MediaIdXxh3Factory {
    fun open(): MediaIdXxh3Session
}

interface MediaIdV32Input : AutoCloseable

interface MediaIdV32AccessUnitInput : MediaIdV32Input {
    val channelCount: Int?
    val durationUs: Long?

    /** Null starts at the first AU; a value requests previous-sync positioning. */
    fun openCursor(sourceTimeUs: Long? = null): MediaIdV32AccessUnitCursor
}

interface MediaIdV32AccessUnitCursor : AutoCloseable {
    fun advance(): Boolean

    val presentationTimeUs: Long?
    val encodedSize: Int

    fun readEncodedBytes(): ByteArray
}

interface MediaIdV32RandomAccessInput : MediaIdV32Input {
    val size: Long

    fun readFully(
        offset: Long,
        bytes: ByteArray,
        destinationOffset: Int = 0,
        length: Int = bytes.size - destinationOffset,
    )
}

enum class MediaIdV32FullScanReason(
    val statusValue: String,
) {
    TIMING("timing"),
    SEEK("seek"),
    OVERLAP("overlap"),
    TAIL("tail"),
}

/**
 * Sole platform-neutral implementation of MDIA-ID-V3.2.
 *
 * The high component uses a bounded head. A tail discriminator is formed only when collision resolution expands
 * a repeated-high bucket. Its encoded-stream path reads a bounded component from three quarters of
 * the stream duration. Raw PCM/float RIFF/WAVE uses a block-aligned head and complete data for the
 * discriminator. No other identity path is present.
 */
abstract class MediaIdV32Generator<S>(
    private val xxh3Factory: MediaIdXxh3Factory,
    private val platform: String,
    private val describeSource: (S) -> String,
    private val benchmarkObserver: MediaIdBenchmarkObserver? = null,
    private val onFullScan: (MediaIdV32FullScanReason) -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
    private val maximumAccessUnitBytes: Int = DEFAULT_MAXIMUM_ACCESS_UNIT_BYTES,
) {
    init {
        require(maximumAccessUnitBytes > 0)
    }

    open fun generateHigh(
        source: S,
        cancellation: MediaIdentityCancellation,
    ): Long =
        generateObserved(source) { input, phases ->
            phases.measure("head.hash") {
                when (input) {
                    is MediaIdV32AccessUnitInput -> hashHighAccessUnits(input, cancellation)
                    is MediaIdV32RandomAccessInput -> hashWaveHigh(input, cancellation)
                    else -> throw MediaIdentityException("UNSUPPORTED_MEDIA_INPUT", "Unsupported Media.ID input adapter")
                }
            }
        }

    open fun generateTail(
        source: S,
        cancellation: MediaIdentityCancellation,
    ): Long =
        generateObserved(source) { input, phases ->
            val outcome =
                when (input) {
                    is MediaIdV32AccessUnitInput -> hashAccessUnits(input, cancellation, phases)
                    is MediaIdV32RandomAccessInput -> hashWaveTail(input, cancellation, phases)
                    else -> throw MediaIdentityException("UNSUPPORTED_MEDIA_INPUT", "Unsupported Media.ID input adapter")
                }
            outcome.copy(digest = normalizeTail(outcome.digest))
        }

    // Observe the same operation used by indexing. A separate benchmark implementation would hide
    // regressions in the ordinary high-only path and could accidentally measure collision work.
    private fun generateObserved(
        source: S,
        operation: (MediaIdV32Input, PhaseAccumulator) -> Outcome,
    ): Long {
        val startNanos = benchmarkObserver?.let { nanoTime() }
        var outcome: Outcome? = null
        var failure: Throwable? = null
        val phases = PhaseAccumulator(startNanos != null, nanoTime)
        try {
            val input = phases.measure("input.open") { openInput(source) }
            input.use { outcome = operation(input, phases) }
            return requireNotNull(outcome).digest
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            if (startNanos != null) {
                val value = outcome
                benchmarkObserver.record(
                    MediaIdBenchmarkSample(
                        source = describeSource(source),
                        platform = platform,
                        implementation = this::class.simpleName ?: "MediaIdV32Generator",
                        success = failure == null,
                        elapsedNanos = (nanoTime() - startNanos).coerceAtLeast(0L),
                        sampleCount = value?.consumedUnits?.takeIf { it > 0UL },
                        selectedOccurrences = value?.consumedUnits?.coerceAtMost(Int.MAX_VALUE.toULong())?.toInt() ?: 0,
                        selectedPayloadBytes = value?.payloadBytes ?: 0L,
                        digest = value?.digest,
                        errorCode =
                            when (failure) {
                                is MediaIdentityException -> failure.code
                                null -> null
                                else -> failure::class.simpleName ?: "MEDIA_ID_FAILED"
                            },
                        phasesElapsedNanos = phases.snapshot(),
                        identityVersion = IDENTITY_VERSION,
                        mode = value?.mode?.label,
                        terminationReason = value?.detail,
                    ),
                )
            }
        }
    }

    protected abstract fun openInput(source: S): MediaIdV32Input

    protected fun supportsWaveRaw(input: MediaIdV32RandomAccessInput): Boolean =
        try {
            parseWave(input)
            true
        } catch (error: MediaIdentityException) {
            if (error.code == "UNSUPPORTED_WAVE_FORMAT") false else throw error
        }

    private fun hashHighAccessUnits(
        input: MediaIdV32AccessUnitInput,
        cancellation: MediaIdentityCancellation,
    ): Outcome {
        val channelCount = validateChannelCount(input.channelCount)
        val componentSession = xxh3Factory.open()
        try {
            componentSession.update(HEAD_DOMAIN)
            var count = 0UL
            var uniqueBytes = 0L
            var payloadBytes = 0L
            val seen = HashSet<Long>()
            input.openCursor().use { cursor ->
                while (cursor.advance()) {
                    cancellation.ensureAvailable()
                    val declaredSize = cursor.encodedSize
                    if (declaredSize <= 0 || declaredSize > maximumAccessUnitBytes) {
                        throw MediaIdentityException(
                            "INVALID_AUDIO_PACKET",
                            "Encoded access-unit size is outside 1..$maximumAccessUnitBytes: $declaredSize",
                        )
                    }
                    val bytes = cursor.readEncodedBytes()
                    if (bytes.size != declaredSize) {
                        throw MediaIdentityException(
                            "TRUNCATED_AUDIO_PACKET",
                            "Encoded access-unit size changed: expected=$declaredSize actual=" + bytes.size,
                        )
                    }
                    if (seen.add(componentSession.hash(bytes))) {
                        uniqueBytes = checkedPayloadAdd(uniqueBytes, bytes.size.toLong())
                    }
                    payloadBytes = checkedPayloadAdd(payloadBytes, bytes.size.toLong())
                    updateComponent(componentSession, count, bytes)
                    count = checkedCountIncrement(count)
                    if (uniqueBytes >= UNIQUE_PAYLOAD_TARGET) break
                }
            }
            if (count == 0UL) throw MediaIdentityException("NO_AUDIO_PACKETS", "No encoded audio access units")
            val digest = componentSession.digest()
            val high =
                xxh3Factory.open().use { identity ->
                    identity.update(HIGH_ID_DOMAIN)
                    identity.update(byteArrayOf(Mode.ACCESS_UNITS.value))
                    writeChannelDescriptor(identity, channelCount)
                    identity.update(uint64(count))
                    identity.update(uint64(digest.toULong()))
                    identity.digest()
                }
            return Outcome(high, count, payloadBytes, Mode.ACCESS_UNITS, "HEAD")
        } finally {
            componentSession.close()
        }
    }

    private fun hashWaveHigh(
        input: MediaIdV32RandomAccessInput,
        cancellation: MediaIdentityCancellation,
    ): Outcome {
        val wave = parseWave(input)
        cancellation.ensureAvailable()
        if (wave.dataLength == 0L) throw MediaIdentityException("NO_AUDIO_PACKETS", "WAVE data chunk is empty")
        val roundedTarget =
            Math.multiplyExact(
                Math.floorDiv(Math.addExact(UNIQUE_PAYLOAD_TARGET, wave.blockAlign - 1L), wave.blockAlign),
                wave.blockAlign,
            )
        val selectedLength = minOf(wave.dataLength, roundedTarget)
        require(selectedLength <= Int.MAX_VALUE)
        val bytes = ByteArray(selectedLength.toInt())
        input.readFully(wave.dataOffset, bytes)
        cancellation.ensureAvailable()
        val high =
            xxh3Factory.open().use { identity ->
                identity.update(HIGH_ID_DOMAIN)
                identity.update(byteArrayOf(Mode.WAVE_RAW.value))
                writeChannelDescriptor(identity, wave.channelCount)
                identity.update(uint64(selectedLength.toULong()))
                identity.update(bytes)
                identity.digest()
            }
        return Outcome(high, 0UL, selectedLength, Mode.WAVE_RAW, "HEAD")
    }

    private fun hashAccessUnits(
        input: MediaIdV32AccessUnitInput,
        cancellation: MediaIdentityCancellation,
        phases: PhaseAccumulator,
    ): Outcome {
        val channelCount = validateChannelCount(input.channelCount)
        val durationUs = input.durationUs
        if (durationUs == null || durationUs <= 0L) {
            return fullOutcome(input, channelCount, cancellation, phases, MediaIdV32FullScanReason.TIMING)
        }
        val head = scanHead(input, cancellation, phases)
        if (head is HeadResult.CompleteFull) {
            return finishFull(channelCount, head.component, phases)
        }
        if (head is HeadResult.InvalidTiming) {
            return fullOutcome(input, channelCount, cancellation, phases, MediaIdV32FullScanReason.TIMING)
        }
        head as HeadResult.Window
        if (durationUs <= head.firstSourceTimeUs) {
            return fullOutcome(input, channelCount, cancellation, phases, MediaIdV32FullScanReason.TIMING)
        }
        val duration = durationUs - head.firstSourceTimeUs
        if (duration <= head.endUs) {
            return fullOutcome(input, channelCount, cancellation, phases, MediaIdV32FullScanReason.TIMING)
        }
        val quarter = duration / 4L
        val requestedStartUs = quarter * 3L + (duration % 4L) * 3L / 4L
        val sourceTimeUs = checkedTimeAdd(head.firstSourceTimeUs, requestedStartUs)
        val tail =
            try {
                scanTail(input, sourceTimeUs, requestedStartUs, head.firstSourceTimeUs, head.endUs, cancellation, phases)
            } catch (error: MediaIdentityException) {
                val reason =
                    when (error.code) {
                        "SEEK_UNAVAILABLE" -> MediaIdV32FullScanReason.SEEK
                        "UNUSABLE_AUDIO_TIMING" -> MediaIdV32FullScanReason.TIMING
                        else -> throw error
                    }
                return fullOutcome(input, channelCount, cancellation, phases, reason)
            }
        return when (tail) {
            TailResult.Overlap -> {
                fullOutcome(input, channelCount, cancellation, phases, MediaIdV32FullScanReason.OVERLAP)
            }

            TailResult.Insufficient -> {
                fullOutcome(input, channelCount, cancellation, phases, MediaIdV32FullScanReason.TAIL)
            }

            is TailResult.Sufficient -> {
                val digest =
                    phases.measure("identity.hash") {
                        finalHeadTail(channelCount, head.component, requestedStartUs, tail.component)
                    }
                Outcome(
                    digest = digest,
                    consumedUnits = checkedCountAdd(head.component.count, tail.component.count),
                    payloadBytes = checkedPayloadAdd(head.component.payloadBytes, tail.component.payloadBytes),
                    mode = Mode.ACCESS_UNITS,
                    detail = Strategy.HEAD_TAIL.label,
                )
            }
        }
    }

    private fun scanHead(
        input: MediaIdV32AccessUnitInput,
        cancellation: MediaIdentityCancellation,
        phases: PhaseAccumulator,
    ): HeadResult {
        val headSession = phases.measure("head.xxh3.open") { xxh3Factory.open() }
        val fullSession = phases.measure("full.xxh3.open") { xxh3Factory.open() }
        try {
            headSession.update(HEAD_DOMAIN)
            fullSession.update(FULL_DOMAIN)
            var firstTimeUs: Long? = null
            var previousTimeUs = -1L
            var count = 0UL
            var payloadBytes = 0L
            var uniqueBytes = 0L
            val seen = HashSet<Long>()
            input.openCursor().use { cursor ->
                while (cursor.advance()) {
                    cancellation.ensureAvailable()
                    val sourceTimeUs = cursor.presentationTimeUs ?: return HeadResult.InvalidTiming
                    val first = firstTimeUs ?: sourceTimeUs.also { firstTimeUs = it }
                    val normalizedTimeUs = sourceTimeUs - first
                    if (sourceTimeUs < 0L || normalizedTimeUs < 0L || normalizedTimeUs < previousTimeUs) {
                        return HeadResult.InvalidTiming
                    }
                    if (uniqueBytes >= UNIQUE_PAYLOAD_TARGET) {
                        if (normalizedTimeUs <= previousTimeUs) return HeadResult.InvalidTiming
                        return HeadResult.Window(
                            component = Component(count, payloadBytes, headSession.digest()),
                            firstSourceTimeUs = first,
                            endUs = normalizedTimeUs,
                        )
                    }
                    val bytes = readAccessUnit(cursor, count, phases, "head.payload_read")
                    if (seen.add(headSession.hash(bytes))) {
                        uniqueBytes = checkedPayloadAdd(uniqueBytes, bytes.size.toLong())
                    }
                    updateComponent(headSession, count, bytes)
                    updateComponent(fullSession, count, bytes)
                    count = checkedCountIncrement(count)
                    payloadBytes = checkedPayloadAdd(payloadBytes, bytes.size.toLong())
                    previousTimeUs = normalizedTimeUs
                }
            }
            if (count == 0UL) throw MediaIdentityException("NO_AUDIO_PACKETS", "No encoded audio access units")
            return HeadResult.CompleteFull(Component(count, payloadBytes, fullSession.digest()))
        } finally {
            headSession.close()
            fullSession.close()
        }
    }

    private fun scanTail(
        input: MediaIdV32AccessUnitInput,
        sourceTimeUs: Long,
        requestedStartUs: Long,
        firstSourceTimeUs: Long,
        headEndUs: Long,
        cancellation: MediaIdentityCancellation,
        phases: PhaseAccumulator,
    ): TailResult {
        val session = phases.measure("tail.xxh3.open") { xxh3Factory.open() }
        try {
            session.update(TAIL_DOMAIN)
            var count = 0UL
            var payloadBytes = 0L
            var uniqueBytes = 0L
            val seen = HashSet<Long>()
            var started = false
            var previousTimeUs = -1L
            input.openCursor(sourceTimeUs).use { cursor ->
                while (cursor.advance()) {
                    cancellation.ensureAvailable()
                    val timestamp = cursor.presentationTimeUs ?: throw timingFailure()
                    val normalizedTimeUs = timestamp - firstSourceTimeUs
                    if (timestamp < 0L || normalizedTimeUs < 0L || normalizedTimeUs < previousTimeUs) {
                        throw timingFailure()
                    }
                    previousTimeUs = normalizedTimeUs
                    if (!started && normalizedTimeUs < requestedStartUs) continue
                    if (!started) {
                        if (normalizedTimeUs < headEndUs) return TailResult.Overlap
                        started = true
                    }
                    val bytes = readAccessUnit(cursor, count, phases, "tail.payload_read")
                    if (seen.add(session.hash(bytes))) {
                        uniqueBytes = checkedPayloadAdd(uniqueBytes, bytes.size.toLong())
                    }
                    updateComponent(session, count, bytes)
                    count = checkedCountIncrement(count)
                    payloadBytes = checkedPayloadAdd(payloadBytes, bytes.size.toLong())
                    if (uniqueBytes >= UNIQUE_PAYLOAD_TARGET) {
                        return TailResult.Sufficient(Component(count, payloadBytes, session.digest()))
                    }
                }
            }
            return TailResult.Insufficient
        } finally {
            session.close()
        }
    }

    private fun fullOutcome(
        input: MediaIdV32AccessUnitInput,
        channelCount: Int?,
        cancellation: MediaIdentityCancellation,
        phases: PhaseAccumulator,
        reason: MediaIdV32FullScanReason,
    ): Outcome {
        onFullScan(reason)
        val component = hashFull(input, cancellation, phases)
        return finishFull(channelCount, component, phases)
    }

    private fun hashFull(
        input: MediaIdV32AccessUnitInput,
        cancellation: MediaIdentityCancellation,
        phases: PhaseAccumulator,
    ): Component {
        val session = phases.measure("full.xxh3.open") { xxh3Factory.open() }
        try {
            session.update(FULL_DOMAIN)
            var count = 0UL
            var payloadBytes = 0L
            input.openCursor().use { cursor ->
                while (cursor.advance()) {
                    cancellation.ensureAvailable()
                    val bytes = readAccessUnit(cursor, count, phases, "full.payload_read")
                    updateComponent(session, count, bytes)
                    count = checkedCountIncrement(count)
                    payloadBytes = checkedPayloadAdd(payloadBytes, bytes.size.toLong())
                }
            }
            if (count == 0UL) throw MediaIdentityException("NO_AUDIO_PACKETS", "No encoded audio access units")
            return Component(count, payloadBytes, session.digest())
        } finally {
            session.close()
        }
    }

    private fun finishFull(
        channelCount: Int?,
        component: Component,
        phases: PhaseAccumulator,
    ): Outcome =
        Outcome(
            digest = phases.measure("identity.hash") { finalFull(channelCount, component) },
            consumedUnits = component.count,
            payloadBytes = component.payloadBytes,
            mode = Mode.ACCESS_UNITS,
            detail = Strategy.FULL.label,
        )

    private fun finalHeadTail(
        channelCount: Int?,
        head: Component,
        requestedStartUs: Long,
        tail: Component,
    ): Long =
        xxh3Factory.open().use { session ->
            session.update(TAIL_ID_DOMAIN)
            session.update(byteArrayOf(Mode.ACCESS_UNITS.value))
            writeChannelDescriptor(session, channelCount)
            session.update(byteArrayOf(Strategy.HEAD_TAIL.value))
            session.update(uint64(head.count))
            session.update(uint64(head.digest.toULong()))
            session.update(uint64(requestedStartUs.toULong()))
            session.update(uint64(tail.count))
            session.update(uint64(tail.digest.toULong()))
            session.digest()
        }

    private fun finalFull(
        channelCount: Int?,
        full: Component,
    ): Long =
        xxh3Factory.open().use { session ->
            session.update(TAIL_ID_DOMAIN)
            session.update(byteArrayOf(Mode.ACCESS_UNITS.value))
            writeChannelDescriptor(session, channelCount)
            session.update(byteArrayOf(Strategy.FULL.value))
            session.update(uint64(full.count))
            session.update(uint64(full.digest.toULong()))
            session.digest()
        }

    private fun hashWaveTail(
        input: MediaIdV32RandomAccessInput,
        cancellation: MediaIdentityCancellation,
        phases: PhaseAccumulator,
    ): Outcome {
        val wave = phases.measure("wave.parse") { parseWave(input) }
        cancellation.ensureAvailable()
        if (wave.dataLength == 0L) throw MediaIdentityException("NO_AUDIO_PACKETS", "WAVE data chunk is empty")
        val digest =
            phases.measure("identity.hash") {
                xxh3Factory.open().use { session ->
                    session.update(TAIL_ID_DOMAIN)
                    session.update(byteArrayOf(Mode.WAVE_RAW.value))
                    writeChannelDescriptor(session, wave.channelCount)
                    session.update(uint64(wave.dataLength.toULong()))
                    val bytes = ByteArray(FULL_WAVE_BUFFER_BYTES)
                    var offset = 0L
                    while (offset < wave.dataLength) {
                        cancellation.ensureAvailable()
                        val length = minOf(bytes.size.toLong(), wave.dataLength - offset).toInt()
                        input.readFully(wave.dataOffset + offset, bytes, length = length)
                        session.update(bytes, length = length)
                        offset += length
                    }
                    session.digest()
                }
            }
        return Outcome(digest, 0UL, wave.dataLength, Mode.WAVE_RAW, Strategy.FULL.label)
    }

    private fun readAccessUnit(
        cursor: MediaIdV32AccessUnitCursor,
        index: ULong,
        phases: PhaseAccumulator,
        phase: String,
    ): ByteArray {
        if (index > UINT32_MAX) throw MediaIdentityException("TOO_MANY_AUDIO_PACKETS", "Access-unit index exceeds uint32")
        val declaredSize = cursor.encodedSize
        if (declaredSize <= 0 || declaredSize > maximumAccessUnitBytes) {
            throw MediaIdentityException(
                "INVALID_AUDIO_PACKET",
                "Encoded access-unit size is outside 1..$maximumAccessUnitBytes: $declaredSize",
            )
        }
        val bytes = phases.measure(phase) { cursor.readEncodedBytes() }
        if (bytes.size != declaredSize) {
            throw MediaIdentityException(
                "TRUNCATED_AUDIO_PACKET",
                "Encoded access-unit size changed: expected=$declaredSize actual=${bytes.size}",
            )
        }
        return bytes
    }

    private fun updateComponent(
        session: MediaIdXxh3Session,
        index: ULong,
        bytes: ByteArray,
    ) {
        session.update(uint32Pair(index, bytes.size))
        session.update(bytes)
    }

    private fun parseWave(input: MediaIdV32RandomAccessInput): WaveFormat {
        if (input.size < 12L) throw invalidWave("RIFF/WAVE header is truncated")
        val header = ByteArray(12)
        input.readFully(0L, header)
        if (!header.regionMatchesAscii(0, "RIFF") || !header.regionMatchesAscii(8, "WAVE")) {
            throw invalidWave("Source is not RIFF/WAVE")
        }
        val riffEnd = checkedWaveAdd(8L, header.uint32Le(4))
        if (riffEnd > input.size || riffEnd < 12L) throw invalidWave("RIFF length exceeds source bounds")
        var offset = 12L
        var format: WaveFormatChunk? = null
        var data: WaveDataChunk? = null
        val chunkHeader = ByteArray(8)
        while (offset + 8L <= riffEnd) {
            input.readFully(offset, chunkHeader)
            val chunkSize = chunkHeader.uint32Le(4)
            val payloadOffset = offset + 8L
            val payloadEnd = checkedWaveAdd(payloadOffset, chunkSize)
            val paddedEnd = checkedWaveAdd(payloadEnd, chunkSize and 1L)
            if (payloadEnd > riffEnd || paddedEnd > riffEnd) throw invalidWave("WAVE chunk exceeds RIFF bounds")
            when {
                chunkHeader.regionMatchesAscii(0, "fmt ") -> {
                    if (format != null) throw invalidWave("Multiple fmt chunks are unsupported")
                    format = parseWaveFormat(input, payloadOffset, chunkSize)
                }

                chunkHeader.regionMatchesAscii(0, "data") -> {
                    if (data != null) throw invalidWave("Multiple data chunks are unsupported")
                    data = WaveDataChunk(payloadOffset, chunkSize)
                }
            }
            offset = paddedEnd
        }
        val fmt = format ?: throw invalidWave("WAVE fmt chunk is missing")
        val audio = data ?: throw invalidWave("WAVE data chunk is missing")
        if (audio.length % fmt.blockAlign != 0L) throw invalidWave("WAVE data is not blockAlign-complete")
        return WaveFormat(fmt.channelCount, fmt.blockAlign, audio.offset, audio.length)
    }

    private fun parseWaveFormat(
        input: MediaIdV32RandomAccessInput,
        offset: Long,
        length: Long,
    ): WaveFormatChunk {
        if (length < 16L) throw invalidWave("WAVE fmt chunk is shorter than 16 bytes")
        val bytes = ByteArray(minOf(length, 40L).toInt())
        input.readFully(offset, bytes)
        val formatCode = bytes.uint16Le(0)
        val channels = bytes.uint16Le(2)
        val blockAlign = bytes.uint16Le(12)
        if (channels == 0 || blockAlign == 0) throw invalidWave("Invalid channel count or blockAlign")
        val supported =
            when (formatCode) {
                WAVE_FORMAT_PCM, WAVE_FORMAT_IEEE_FLOAT -> {
                    true
                }

                WAVE_FORMAT_EXTENSIBLE -> {
                    length >= 40L &&
                        bytes.uint16Le(16) >= 22 &&
                        bytes.uint16Le(24) in setOf(WAVE_FORMAT_PCM, WAVE_FORMAT_IEEE_FLOAT) &&
                        bytes.copyOfRange(26, 40).contentEquals(EXTENSIBLE_GUID_TAIL)
                }

                else -> {
                    false
                }
            }
        if (!supported) {
            throw MediaIdentityException("UNSUPPORTED_WAVE_FORMAT", "Compressed or unsupported WAVE format: $formatCode")
        }
        return WaveFormatChunk(channels, blockAlign.toLong())
    }

    private fun writeChannelDescriptor(
        session: MediaIdXxh3Session,
        channelCount: Int?,
    ) {
        if (channelCount == null) {
            session.update(byteArrayOf(0))
        } else {
            session.update(byteArrayOf(1))
            session.update(uint32(channelCount.toLong()))
        }
    }

    private fun validateChannelCount(value: Int?): Int? {
        if (value != null && value <= 0) {
            throw MediaIdentityException("INVALID_CHANNEL_COUNT", "Channel count must be positive when present: $value")
        }
        return value
    }

    private fun normalizeTail(value: Long): Long {
        if (value != 0L) return value
        var nonce = 0L
        while (true) {
            val retry =
                xxh3Factory.open().use { session ->
                    session.update(ZERO_TAIL_DOMAIN)
                    session.update(uint64(nonce.toULong()))
                    session.digest()
                }
            if (retry != 0L) return retry
            nonce = Math.addExact(nonce, 1L)
        }
    }

    private fun checkedTimeAdd(
        left: Long,
        right: Long,
    ): Long =
        try {
            Math.addExact(left, right)
        } catch (error: ArithmeticException) {
            throw MediaIdentityException("MEDIA_ID_TIMING_OVERFLOW", "Media.ID seek timestamp overflowed", error)
        }

    private fun checkedWaveAdd(
        left: Long,
        right: Long,
    ): Long {
        if (right < 0L || Long.MAX_VALUE - left < right) throw invalidWave("WAVE length overflow")
        return left + right
    }

    private fun invalidWave(message: String) = MediaIdentityException("MALFORMED_WAVE", message)

    private fun timingFailure() = MediaIdentityException("UNUSABLE_AUDIO_TIMING", "Access-unit timing is unusable")

    private sealed interface HeadResult {
        data class Window(
            val component: Component,
            val firstSourceTimeUs: Long,
            val endUs: Long,
        ) : HeadResult

        data class CompleteFull(
            val component: Component,
        ) : HeadResult

        data object InvalidTiming : HeadResult
    }

    private sealed interface TailResult {
        data class Sufficient(
            val component: Component,
        ) : TailResult

        data object Insufficient : TailResult

        data object Overlap : TailResult
    }

    private data class Component(
        val count: ULong,
        val payloadBytes: Long,
        val digest: Long,
    )

    private data class Outcome(
        val digest: Long,
        val consumedUnits: ULong,
        val payloadBytes: Long,
        val mode: Mode,
        val detail: String,
    )

    private data class WaveFormat(
        val channelCount: Int,
        val blockAlign: Long,
        val dataOffset: Long,
        val dataLength: Long,
    )

    private data class WaveFormatChunk(
        val channelCount: Int,
        val blockAlign: Long,
    )

    private data class WaveDataChunk(
        val offset: Long,
        val length: Long,
    )

    private enum class Mode(
        val value: Byte,
        val label: String,
    ) {
        ACCESS_UNITS(0x00, "ACCESS_UNITS"),
        WAVE_RAW(0x01, "WAVE_RAW"),
    }

    private enum class Strategy(
        val value: Byte,
        val label: String,
    ) {
        HEAD_TAIL(0x00, "HEAD_TAIL"),
        FULL(0x01, "FULL"),
    }

    private enum class Termination(
        val value: Byte,
        val label: String,
    ) {
        END_OF_STREAM(0x00, "END_OF_STREAM"),
        HASH_SIZE_REACHED(0x01, "HASH_SIZE_REACHED"),
    }

    private class PhaseAccumulator(
        private val enabled: Boolean,
        private val nanoTime: () -> Long,
    ) {
        private val elapsed = linkedMapOf<String, Long>()

        fun <T> measure(
            name: String,
            operation: () -> T,
        ): T {
            if (!enabled) return operation()
            val start = nanoTime()
            return try {
                operation()
            } finally {
                elapsed[name] = saturatedAdd(elapsed[name] ?: 0L, (nanoTime() - start).coerceAtLeast(0L))
            }
        }

        fun snapshot(): Map<String, Long> = elapsed.toMap()
    }

    companion object {
        const val IDENTITY_VERSION = "MDIA-ID-V3.2"
        const val UNIQUE_PAYLOAD_TARGET = 128L * 1024L
        const val DEFAULT_MAXIMUM_ACCESS_UNIT_BYTES = 4 * 1024 * 1024
        const val FULL_WAVE_BUFFER_BYTES = 1024 * 1024

        private const val UINT32_MAX = 0xffff_ffffUL
        private const val WAVE_FORMAT_PCM = 0x0001
        private const val WAVE_FORMAT_IEEE_FLOAT = 0x0003
        private const val WAVE_FORMAT_EXTENSIBLE = 0xfffe
        private val HIGH_ID_DOMAIN = "$IDENTITY_VERSION-HIGH-ID".toByteArray(StandardCharsets.US_ASCII)
        private val TAIL_ID_DOMAIN = "$IDENTITY_VERSION-TAIL-ID".toByteArray(StandardCharsets.US_ASCII)
        private val ZERO_TAIL_DOMAIN = "$IDENTITY_VERSION-ZERO-TAIL".toByteArray(StandardCharsets.US_ASCII)
        private val HEAD_DOMAIN = "$IDENTITY_VERSION-HEAD".toByteArray(StandardCharsets.US_ASCII)
        private val TAIL_DOMAIN = "$IDENTITY_VERSION-TAIL".toByteArray(StandardCharsets.US_ASCII)
        private val FULL_DOMAIN = "$IDENTITY_VERSION-FULL".toByteArray(StandardCharsets.US_ASCII)
        private val EXTENSIBLE_GUID_TAIL =
            byteArrayOf(
                0x00,
                0x00,
                0x10,
                0x00,
                0x80.toByte(),
                0x00,
                0x00,
                0xaa.toByte(),
                0x00,
                0x38,
                0x9b.toByte(),
                0x71,
                0x00,
                0x00,
            )

        private fun uint32(value: Long): ByteArray {
            require(value in 0L..0xffff_ffffL)
            return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.toInt()).array()
        }

        private fun uint32Pair(
            first: ULong,
            second: Int,
        ): ByteArray =
            ByteBuffer
                .allocate(Int.SIZE_BYTES * 2)
                .putInt(first.toInt())
                .putInt(second)
                .array()

        private fun uint64(value: ULong): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value.toLong()).array()

        private fun checkedCountIncrement(value: ULong): ULong {
            if (value == ULong.MAX_VALUE) throw MediaIdentityException("MEDIA_ID_COUNT_OVERFLOW", "Access-unit count overflowed")
            return value + 1UL
        }

        private fun checkedCountAdd(
            left: ULong,
            right: ULong,
        ): ULong {
            if (ULong.MAX_VALUE - left < right) {
                throw MediaIdentityException("MEDIA_ID_COUNT_OVERFLOW", "Access-unit count overflowed")
            }
            return left + right
        }

        private fun checkedPayloadAdd(
            left: Long,
            right: Long,
        ): Long {
            if (right < 0L || Long.MAX_VALUE - left < right) {
                throw MediaIdentityException("MEDIA_ID_PAYLOAD_OVERFLOW", "Media.ID payload byte count overflowed")
            }
            return left + right
        }

        private fun saturatedAdd(
            left: Long,
            right: Long,
        ): Long = if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

        private fun ByteArray.regionMatchesAscii(
            offset: Int,
            value: String,
        ): Boolean = value.indices.all { this[offset + it].toInt() and 0xff == value[it].code }

        private fun ByteArray.uint16Le(offset: Int): Int =
            ByteBuffer
                .wrap(this, offset, Short.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt() and 0xffff

        private fun ByteArray.uint32Le(offset: Int): Long =
            ByteBuffer
                .wrap(this, offset, Int.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .toLong() and 0xffff_ffffL
    }
}
