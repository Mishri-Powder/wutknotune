package link.roadio.cuegrain

import link.roadio.cuegrain.*
import link.roadio.cuegrain.identity.*

fun acquireWave(
    input: MediaInput,
    xxh3: MediaIdXxh3Factory,
    cancellation: MediaIdentityCancellation,
): MediaAcquisition {
    val generator =
        object : MediaIdV32Generator<MediaInput>(
            xxh3,
            "example",
            { it.id },
        ) {
            override fun openInput(source: MediaInput): MediaIdV32Input =
                object : MediaIdV32RandomAccessInput {
                    override val size = source.length

                    override fun readFully(
                        offset: Long,
                        bytes: ByteArray,
                        destinationOffset: Int,
                        length: Int,
                    ) {
                        source.read(offset, length).copyInto(bytes, destinationOffset)
                    }

                    override fun close() = Unit
                }
        }
    val reader =
        Cuegrain(
            ParseOptions(cancelled = {
                cancellation.ensureAvailable()
                false
            }),
        )
    return reader.acquire(input, "wav") { shared ->
        MediaIdentity(generator.generateHigh(shared, cancellation))
    }
}
