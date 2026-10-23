# Cuegrain media identity acquisition

Package `link.roadio.cuegrain.identity` owns the Android-free MDIA-ID-V3.2 algorithm.
[Riff's identity owner](/Riff/LATEST/media-id-scope-and-terminology) defines production framing.
Consumers inject unseeded XXH3-64, cancellation and AU/random-access inputs. Desktop/Android
equivalence is not required; no backend identifier or stored discriminator is added.

## Acquisition

```kotlin
data class MediaIdentity(val high: Long, val low: Long? = null)
data class MediaAcquisition(val report: Report, val media_id: MediaIdentity)
fun Cuegrain.acquire(
    input: MediaInput,
    hint: String? = null,
    identity: (MediaInput) -> MediaIdentity,
): MediaAcquisition
```

The member method parses metadata and invokes the explicit identity callback through the same
CachedMediaInput. Ordinary read never starts identity. INPUT_IO throws IOException; cancellation and
callback failures propagate. There is no implicit whole-container identity fallback. The caller owns
the input through acquisition and any later Report artwork access; media_id carries no resources.

`CachedMediaInput(input: MediaInput, maximumBytes: Int = 262_144)` implements MediaInput and exposes
its borrowed input. The positive byte budget bounds exact-range copies; no read-ahead or full-file
copy occurs. Returned arrays cannot mutate cached bytes. Reads are synchronized; wrapping never
takes ownership or closes the source.

## Generator and adapters

```kotlin
abstract class MediaIdV32Generator<S>(
    xxh3Factory: MediaIdXxh3Factory,
    platform: String,
    describeSource: (S) -> String,
    benchmarkObserver: MediaIdBenchmarkObserver? = null,
    onFullScan: (MediaIdV32FullScanReason) -> Unit = {},
    nanoTime: () -> Long = System::nanoTime,
    maximumAccessUnitBytes: Int = 4 * 1024 * 1024,
) {
    protected abstract fun openInput(source: S): MediaIdV32Input
    open fun generateHigh(source: S, cancellation: MediaIdentityCancellation): Long
    open fun generateTail(source: S, cancellation: MediaIdentityCancellation): Long
    protected fun supportsWaveRaw(input: MediaIdV32RandomAccessInput): Boolean
}
```

The generator closes acquired inputs, hash sessions and cursors. A borrowed-source adapter uses
no-op close without closing its caller's descriptor. Platform/source labels serve diagnostics only;
neither enters identity or stored provenance. IDENTITY_VERSION is MDIA-ID-V3.2 and
UNIQUE_PAYLOAD_TARGET is 131072 bytes. Positive AU size bounds reject oversized packets.
MediaIdV32FullScanReason reports TIMING, SEEK, OVERLAP or TAIL fallback to adopted full acquisition.
Low expansion and rereading the retained original are ordinary collision work.

MediaIdentityCancellation exposes cancelled: Boolean and ensureAvailable().
MediaIdentityException(code, message, cause = null) is an open RuntimeException; cancellation stays
distinct. MediaIdXxh3Factory.open() creates an AutoCloseable MediaIdXxh3Session with update(bytes,
offset = 0, length = bytes.size - offset), hash(bytes): Long and digest(): Long. The core adds no
hash-runtime dependency.

| Input                       | Members                                                                                                       |
| --------------------------- | ------------------------------------------------------------------------------------------------------------- |
| MediaIdV32Input             | AutoCloseable base                                                                                            |
| MediaIdV32AccessUnitInput   | nullable channelCount/durationUs; openCursor(sourceTimeUs: Long? = null)                                      |
| MediaIdV32AccessUnitCursor  | advance(): Boolean; nullable presentationTimeUs; encodedSize; readEncodedBytes(): ByteArray; close()          |
| MediaIdV32RandomAccessInput | size: Long; readFully(offset, bytes, destinationOffset = 0, length = bytes.size - destinationOffset); close() |

Null cursor position starts at the first AU; a timed position requests previous-sync positioning.
The algorithm discards pre-roll according to the adopted timeline. Raw WAVE bytes and AU payload,
channels, timestamps and alignment must be actual adapter observations. Path/tag text never enters
audio identity. MediaIdBenchmarkObserver.record(sample) is optional operational telemetry; it does
not affect output or own source resources.

## Fragment component

```kotlin
fun fragmentIdentityComponent(
    contentComponent: Long, beginUs: Long?, endUs: Long?,
    low: Boolean, hash: (ByteArray) -> Long,
): Long
```

The existing 27-byte big-endian frame binds known content to nullable clip bounds (high domain 0x46,
low domain 0x4c). Present bounds are nonnegative; present end exceeds present begin. Derivation
opens no source. A missing base low still requires ordinary L1 collision acquisition.

## Caller-owned WAVE example

```kotlin
import link.roadio.cuegrain.*
import link.roadio.cuegrain.identity.*

fun acquireWave(
    input: MediaInput,
    xxh3: MediaIdXxh3Factory,
    cancellation: MediaIdentityCancellation,
): MediaAcquisition {
    val generator = object : MediaIdV32Generator<MediaInput>(
        xxh3, "example", { it.id },
    ) {
        override fun openInput(source: MediaInput): MediaIdV32Input =
            object : MediaIdV32RandomAccessInput {
                override val size = source.length
                override fun readFully(
                    offset: Long, bytes: ByteArray, destinationOffset: Int, length: Int,
                ) {
                    source.read(offset, length).copyInto(bytes, destinationOffset)
                }
                override fun close() = Unit
            }
    }
    val reader = Cuegrain(ParseOptions(cancelled = {
        cancellation.ensureAvailable()
        false
    }))
    return reader.acquire(input, "wav") { shared ->
        MediaIdentity(generator.generateHigh(shared, cancellation))
    }
}
```

Use a live supported PCM/float WAVE input and an unseeded XXH3-64 factory. Compressed formats need
an encoded-AU adapter. Core has no Android, SAF, Media3 or Riff dependency.
