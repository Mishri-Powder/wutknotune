package link.roadio.riff.indexer.core.pipeline

import link.roadio.cuetree.processOrderedPipeline
import link.roadio.riff.indexer.core.CancellationSignal
import java.util.concurrent.CancellationException

/** One producer performs acquisition; the caller integrates work in input order. */
internal fun <Input, Prepared> processBoundedPipeline(
    workset: List<Input>,
    maxInFlight: Int,
    cancellation: CancellationSignal,
    producerThreadName: String,
    prepare: (Input, CancellationSignal) -> Prepared,
    consume: (Prepared) -> Unit,
) {
    processOrderedPipeline(
        workset,
        maxInFlight,
        { cancellation.cancelled },
        producerThreadName,
        prepare = { item, cancelled ->
            prepare(
                item,
                object : CancellationSignal {
                    override val cancelled: Boolean get() = cancelled()

                    override fun ensureAvailable() {
                        if (cancelled()) throw CancellationException("$producerThreadName stopped")
                    }
                },
            )
        },
        consume = consume,
    )
}
