package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.AcquisitionGuard
import link.roadio.cuetree.AcquisitionMode
import link.roadio.cuetree.AcquisitionOutcome
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.Node
import link.roadio.cuetree.SourceAcquisition
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.android.ipc.AndroidIndexerArtifactBinder
import link.roadio.riff.indexer.android.ipc.ReliableCommands
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttemptMode
import java.io.IOException
import java.util.UUID

class ScanJournalUnavailable(
    cause: Throwable,
) : IOException("Player attempt journal unavailable", cause)

/** Physical source attempts are acknowledged before opening a descriptor. */
class AndroidScanAcquisitionGuard(
    private val binder: AndroidIndexerArtifactBinder,
) : AcquisitionGuard {
    private val recovery = mutableMapOf<List<String>, AcquisitionMode>()
    private val active = ThreadLocal<String?>()

    fun loadRecovery() {
        recovery.clear()
        var offset = 0
        do {
            val fields = reply(ReliableCommands.RECOVER_SCAN, listOf(offset.toString()))
            val total = fields.first().toInt()
            require((fields.size - 1) % 9 == 0)
            fields.drop(1).chunked(9).forEach { record ->
                val source = record.subList(2, 8).toList()
                recovery[source] =
                    if (ScanAttemptMode.valueOf(record[8]) == ScanAttemptMode.ISOLATED) {
                        AcquisitionMode.QUARANTINE
                    } else {
                        AcquisitionMode.ISOLATED
                    }
            }
            offset += (fields.size - 1) / 9
        } while (offset < total)
    }

    private fun physicalKey(
        document: String,
        node: Node,
        stage: String,
        span: Span?,
    ): List<String> = scanSourceKey(document, requireNotNull(requireNotNull(node.source).stamp), stage, span)

    private fun key(source: SourceAcquisition): List<String> {
        val provider = source.provider as AndroidSafProvider
        return scanSourceKey(provider.documentUri(source.handle), source.stamp, "L1", null)
    }

    fun clearStage(stage: String) {
        require(stage == "L1" || stage == "L2")
        check(reply(ReliableCommands.CLEAR_SCAN, listOf(stage)) == listOf("OK"))
        recovery.keys.removeAll { it[3] == stage }
    }

    data class StageToken(
        val reference: String,
    )

    fun modeForL2(
        node: Node,
        document: String,
        span: Span?,
    ): AcquisitionMode = recovery[physicalKey(document, node, "L2", span)] ?: AcquisitionMode.NORMAL

    fun beginL2(
        node: Node,
        document: String,
        span: Span?,
        mode: AcquisitionMode,
    ): StageToken {
        require(mode != AcquisitionMode.QUARANTINE)
        val reference = UUID.randomUUID().toString()
        val fields =
            listOf(binder.serverEpoch, reference) + physicalKey(document, node, "L2", span) +
                if (mode == AcquisitionMode.ISOLATED) "ISOLATED" else "NORMAL"
        val result = reply(ReliableCommands.BEGIN_SCAN, fields).single()
        if (!(
                (
                    result == "NORMAL" &&
                        mode == AcquisitionMode.NORMAL
                ) ||
                    (
                        (result == "ISOLATE" || result == "NORMAL") &&
                            mode == AcquisitionMode.ISOLATED
                    )
            )
        ) {
            throw ScanJournalUnavailable(IllegalStateException("L2 recovery mode changed: " + result))
        }
        return StageToken(reference)
    }

    fun finishL2(token: StageToken) {
        reply(ReliableCommands.END_SCAN, listOf(binder.serverEpoch, token.reference))
    }

    override fun mode(source: SourceAcquisition): AcquisitionMode = recovery[key(source)] ?: AcquisitionMode.NORMAL

    override fun begin(
        source: SourceAcquisition,
        mode: AcquisitionMode,
        cancellation: Cancellation,
    ) {
        cancellation.check()
        require(mode != AcquisitionMode.QUARANTINE)
        val reference = UUID.randomUUID().toString()
        val fields =
            listOf(binder.serverEpoch, reference) + key(source) +
                if (mode == AcquisitionMode.ISOLATED) "ISOLATED" else "NORMAL"
        val result = reply(ReliableCommands.BEGIN_SCAN, fields).single()
        if (!(
                (
                    result == "NORMAL" &&
                        mode == AcquisitionMode.NORMAL
                ) ||
                    (
                        (result == "ISOLATE" || result == "NORMAL") &&
                            mode == AcquisitionMode.ISOLATED
                    )
            )
        ) {
            throw ScanJournalUnavailable(IllegalStateException("Scan recovery mode changed: " + result))
        }
        active.set(reference)
    }

    override fun finish(
        source: SourceAcquisition,
        outcome: AcquisitionOutcome,
    ) {
        val reference = active.get() ?: return
        active.remove()
        reply(ReliableCommands.END_SCAN, listOf(binder.serverEpoch, reference))
    }

    private fun reply(
        type: Int,
        fields: List<String>,
    ): List<String> {
        val response =
            try {
                binder.requestReliable(type, fields).get()
            } catch (error: Exception) {
                throw ScanJournalUnavailable(error)
            }
        try {
            if (response.fields.firstOrNull() ==
                "ERROR"
            ) {
                throw ScanJournalUnavailable(
                    IOException(
                        "Player journal refused scan attempt: " + response.fields.drop(1).joinToString(),
                    ),
                )
            }
            return response.fields
        } finally {
            response.close()
        }
    }
}
