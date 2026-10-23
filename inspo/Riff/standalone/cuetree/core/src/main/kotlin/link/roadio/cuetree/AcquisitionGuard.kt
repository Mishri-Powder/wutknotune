// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

enum class AcquisitionMode { NORMAL, ISOLATED, QUARANTINE }

enum class AcquisitionOutcome { SUCCESS, FAILURE, CANCELLED }

data class SourceAcquisition(
    val provider: SourceProvider,
    val handle: SourceHandle,
    val stamp: Stamp,
    val parentId: MediaId,
    val name: String,
    val hint: String,
)

/** Hosts own IPC/death evidence; Cuetree owns source acquisition and factual integration. */
interface AcquisitionGuard {
    fun mode(source: SourceAcquisition): AcquisitionMode = AcquisitionMode.NORMAL

    fun begin(
        source: SourceAcquisition,
        mode: AcquisitionMode,
        cancellation: Cancellation,
    ) = Unit

    fun finish(
        source: SourceAcquisition,
        outcome: AcquisitionOutcome,
    ) = Unit

    fun failed(source: SourceAcquisition) = Unit

    fun resolved(source: SourceAcquisition) = Unit
}

object UnguardedAcquisition : AcquisitionGuard
