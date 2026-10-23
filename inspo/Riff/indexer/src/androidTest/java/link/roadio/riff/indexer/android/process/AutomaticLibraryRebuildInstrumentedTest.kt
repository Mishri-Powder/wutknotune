package link.roadio.riff.indexer.android.process

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SnapshotStore
import link.roadio.riff.indexer.android.cuetree.AndroidCuetreeIdentity
import link.roadio.riff.indexer.android.ipc.ReliableAndroidPeer
import link.roadio.riff.indexer.android.ipc.ReliableCommands
import link.roadio.riff.indexer.android.ipc.ReliableWire
import link.roadio.riff.indexer.core.ipc.reliable.ReliableAck
import link.roadio.riff.indexer.core.ipc.reliable.ReliablePacket
import link.roadio.riff.indexer.core.ipc.reliable.ReliableResult
import link.roadio.riff.ipc.indexer.IIndexerArtifactService
import link.roadio.riff.ipc.player.IPlayerArtifactCallback
import link.roadio.riff.storage.hash.nativeXxh3
import link.roadio.riff.storage.root.ApplicationDataStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AutomaticLibraryRebuildInstrumentedTest {
    @Test fun incompatibleFormatAutomaticallyRecreatesBothScopesAfterJournalAck() = exerciseRecreation(true)

    @Test fun obsoleteMetadataAutomaticallyRecreatesBothScopesAfterJournalAck() = exerciseRecreation(false)

    private fun exerciseRecreation(oldFormat: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().context
        // The library test APK is its own disposable target. Fail before any filesystem mutation
        // if this test is ever routed to a working application or an external data root.
        assertEquals("link.roadio.riff.indexer.module.test", context.packageName)
        val component = ComponentName(context, IndexerProcessService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertEquals(context.packageName, serviceInfo.applicationInfo.packageName)
        val applicationRoot = ApplicationDataStorage(context).activeRoot()
        assertEquals(File(context.noBackupFilesDir, "riff-data-v1").canonicalFile, applicationRoot.canonicalFile)
        val directory = File(applicationRoot, "indexer/cuetree")
        directory.deleteRecursively()
        val cohort =
            SnapshotStore(
                directory.toPath(),
            ).write(Snapshot(41, AndroidCuetreeIdentity(context).scheme, emptyList(), metadataRevision = "obsolete-policy"))
        val files = File(directory, cohort).listFiles()!!.filter { it.name in FlatCatalogs.names }
        assertEquals(4, files.size)
        for (file in if (oldFormat) files else emptyList()) {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val root = buffer.getInt(0)
            val rootVtable = root - buffer.getInt(root)
            val headerReference = root + (buffer.getShort(rootVtable + 4).toInt() and 0xffff)
            val header = headerReference + buffer.getInt(headerReference)
            val headerVtable = header - buffer.getInt(header)
            val version = header + (buffer.getShort(headerVtable + 4).toInt() and 0xffff)
            assertEquals(FlatCatalogs.FORMAT_VERSION.toInt(), buffer.getInt(version))
            buffer.putInt(version, (FlatCatalogs.FORMAT_VERSION - 1).toInt())
            file.writeBytes(bytes)
        }
        File(directory, cohort + "/MANIFEST").writeText(
            files.sortedBy { it.name }.joinToString("") {
                String.format(Locale.ROOT, "%s %d %016x\n", it.name, it.length(), nativeXxh3(it.readBytes()))
            },
        )
        File(directory, "LAST_GENERATION").writeText("41\n")
        val analysis =
            File(directory, "analysis/sentinel").apply {
                parentFile!!.mkdirs()
                writeText("analysis must remain")
            }
        val player =
            File(applicationRoot, "player/rebuild-test-sentinel").apply {
                parentFile!!.mkdirs()
                writeText("player must remain")
            }
        val analysisWatermark = File(directory, "analysis/LAST_GENERATION").apply { writeText("19\n") }
        val source =
            File(applicationRoot, "source-rebuild-fixture/track").apply {
                parentFile!!.mkdirs()
                writeText("source must remain")
            }
        Files.createSymbolicLink(File(directory, "external-link").toPath(), source.parentFile!!.toPath())
        val original = files.associate { it.name to it.readBytes().toList() }
        val bound = CountDownLatch(1)
        val journalRequested = CountDownLatch(1)
        val journalAcknowledged = CountDownLatch(1)
        val journalClears = AtomicInteger()
        val statuses = CopyOnWriteArrayList<String>()
        val published = CountDownLatch(1)
        lateinit var service: IIndexerArtifactService
        val client =
            ReliableAndroidPeer { packet ->
                ReliableResult(
                    when (packet.type) {
                        ReliableCommands.CLEAR_SCAN -> {
                            if (packet.fields.isEmpty()) {
                                journalClears.incrementAndGet()
                                journalRequested.countDown()
                                check(journalAcknowledged.await(15, TimeUnit.SECONDS))
                            }
                            listOf("OK")
                        }

                        ReliableCommands.RECOVER_SCAN -> {
                            listOf("0")
                        }

                        ReliableCommands.BEGIN_SCAN -> {
                            listOf("NORMAL")
                        }

                        else -> {
                            listOf("OK")
                        }
                    },
                )
            }
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
                    attachment: link.roadio.riff.ipc.indexer.IndexerArtifactHandle?,
                ) {
                    client.receiveAck(session, seq, fields, attachment)
                }

                override fun onIndexerArtifactAvailable(
                    artifactKind: String,
                    volumeId: String,
                    generation: Long,
                ) {
                    if (artifactKind == "CUMD") published.countDown()
                }

                override fun onIndexerStatusChanged(status: String) {
                    statuses += status
                }

                override fun onSafListingIssueDetected(
                    activeRootCount: Int,
                    activeFolderLabels: Array<out String>?,
                    unverifiedRootCount: Int,
                    unverifiedFolderLabels: Array<out String>?,
                ) = Unit
            }
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    binder: IBinder,
                ) {
                    service = IIndexerArtifactService.Stub.asInterface(binder)
                    service.registerPlayerArtifactCallback(callback)
                    val serverEpoch = service.openReliableChannel(callback, "explicit-rebuild-test")
                    client.connect(
                        "explicit-rebuild-test|" + serverEpoch,
                        object : ReliableWire {
                            override fun sendPacket(packet: ReliablePacket) {
                                service.receiveReliablePacket(packet.session, packet.seq, packet.type, packet.fields.toTypedArray())
                            }

                            override fun sendAck(ack: ReliableAck) {
                                service.receiveReliableAck(ack.session, ack.seq, ack.result.fields.toTypedArray())
                            }
                        },
                    )
                    bound.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
        val intent = Intent(context, IndexerProcessService::class.java)
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(bound.await(15, TimeUnit.SECONDS))
            assertTrue("Automatic reset must request journal clearance", journalRequested.await(15, TimeUnit.SECONDS))
            assertEquals(original, files.associate { it.name to it.readBytes().toList() })
            assertTrue(analysis.exists())
            assertFalse(published.await(200, TimeUnit.MILLISECONDS))
            journalAcknowledged.countDown()
            assertTrue("Automatic recreation must publish without a manual command", published.await(30, TimeUnit.SECONDS))
            assertEquals(1, journalClears.get())
            assertFalse(statuses.contains("Music library needs rebuilding"))
            val snapshot = SnapshotStore(directory.toPath()).read()
            assertNotNull(snapshot)
            assertTrue(snapshot!!.generation > 41)
            assertFalse("AU scope must discard old analysis", analysis.exists())
            assertTrue(analysisWatermark.readText().trim().toLong() >= 19L)
            assertEquals("player must remain", player.readText())
            assertEquals("source must remain", source.readText())
            assertFalse(File(directory, cohort).exists())
        } finally {
            journalAcknowledged.countDown()
            runCatching { service.quiesceForStorageMigration() }
            runCatching { service.unregisterPlayerArtifactCallback(callback) }
            context.unbindService(connection)
            client.close()
            player.delete()
            source.delete()
            source.parentFile!!.delete()
        }
    }
}
