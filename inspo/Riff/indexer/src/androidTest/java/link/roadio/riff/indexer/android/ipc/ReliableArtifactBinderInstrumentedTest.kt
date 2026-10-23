package link.roadio.riff.indexer.android.ipc

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.ipc.IndexerArtifactKind
import link.roadio.riff.indexer.core.ipc.IndexerArtifactMetadata
import link.roadio.riff.indexer.core.ipc.reliable.ReliableAck
import link.roadio.riff.indexer.core.ipc.reliable.ReliablePacket
import link.roadio.riff.indexer.core.ipc.reliable.ReliableResult
import link.roadio.riff.ipc.indexer.IndexerArtifactHandle
import link.roadio.riff.ipc.player.IPlayerArtifactCallback
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class ReliableArtifactBinderInstrumentedTest {
    @Test fun descriptorAckTransfersFreshOwnedDuplicate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.cacheDir.resolve("reliable-artifact-test").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val metadata = IndexerArtifactMetadata(IndexerArtifactKind.CUMD, "test", 1, 4, 9, 1, 0)
        val server =
            AndroidIndexerArtifactBinder(
                AndroidIndexerArtifactCatalog { _, _ ->
                    ValidatedAndroidArtifact(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), metadata)
                },
            )
        val client = ReliableAndroidPeer { ReliableResult(listOf("OK")) }
        val callback =
            object : IPlayerArtifactCallback.Stub() {
                override fun receiveReliablePacket(
                    session: String,
                    seq: Long,
                    type: Int,
                    fields: Array<out String>?,
                ) {
                    client.receivePacket(session, seq, type, fields)
                }

                override fun receiveReliableAck(
                    session: String,
                    seq: Long,
                    fields: Array<out String>?,
                    attachment: IndexerArtifactHandle?,
                ) {
                    // A local Binder double explicitly copies the FD, as cross-process Binder marshalling does.
                    client.receiveAck(session, seq, fields, attachment?.let(::duplicateArtifact))
                }

                override fun onIndexerArtifactAvailable(
                    kind: String,
                    volume: String,
                    generation: Long,
                ) = Unit

                override fun onIndexerStatusChanged(status: String) = Unit

                override fun onSafListingIssueDetected(
                    activeRootCount: Int,
                    activeFolderLabels: Array<out String>?,
                    unverifiedRootCount: Int,
                    unverifiedFolderLabels: Array<out String>?,
                ) = Unit
            }
        try {
            val serverEpoch = server.openReliableChannel(callback, "player-test")
            client.connect(
                "player-test|" + serverEpoch,
                object : ReliableWire {
                    override fun sendPacket(packet: ReliablePacket) {
                        server.receiveReliablePacket(packet.session, packet.seq, packet.type, packet.fields.toTypedArray())
                    }

                    override fun sendAck(ack: ReliableAck) {
                        server.receiveReliableAck(ack.session, ack.seq, ack.result.fields.toTypedArray())
                    }
                },
            )
            val first = client.request(ReliableCommands.READ, listOf("CUMD", "test")).get(5, TimeUnit.SECONDS)
            assertEquals(listOf("OK"), first.fields)
            val handle = (first.attachment as ArtifactReply).handle
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), FileInputStream(handle.descriptor!!.fileDescriptor).use { it.readBytes() })
            first.close()
            val second = client.request(ReliableCommands.READ, listOf("CUMD", "test")).get(5, TimeUnit.SECONDS)
            assertNotNull(second.attachment)
            second.close()
        } finally {
            client.close()
            server.close()
            file.delete()
        }
    }
}
