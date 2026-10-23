// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import java.io.IOException
import java.util.concurrent.CancellationException

/** Only a completed source operation can become a persistent source failure. */
internal class SourceAcquisitionFailure(
    cause: Throwable,
) : IOException("Source acquisition failed", cause)

internal fun <T> AcquisitionGuard.acquire(
    source: SourceAcquisition,
    mode: AcquisitionMode,
    cancellation: Cancellation,
    read: () -> T,
): T {
    cancellation.check()
    if (mode == AcquisitionMode.QUARANTINE) {
        throw SourceAcquisitionFailure(IOException("Repeated Indexer termination during isolated L1 acquisition"))
    }
    // Journal/control failures must escape callers' recoverable provider-IOException handlers.
    try {
        begin(source, mode, cancellation)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw IllegalStateException("Acquisition journal begin failed", error)
    }
    var outcome = AcquisitionOutcome.CANCELLED
    try {
        return read().also { outcome = AcquisitionOutcome.SUCCESS }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        cancellation.check()
        outcome = AcquisitionOutcome.FAILURE
        throw SourceAcquisitionFailure(error)
    } catch (error: OutOfMemoryError) {
        cancellation.check()
        outcome = AcquisitionOutcome.FAILURE
        throw SourceAcquisitionFailure(error)
    } finally {
        try {
            finish(source, outcome)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("Acquisition journal finish failed", error)
        }
    }
}
