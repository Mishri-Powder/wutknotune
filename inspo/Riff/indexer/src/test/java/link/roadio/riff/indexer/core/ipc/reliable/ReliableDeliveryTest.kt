package link.roadio.riff.indexer.core.ipc.reliable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CompletableFuture

class ReliableDeliveryTest {
    private class Link {
        var now = 0L
        var dropData = 0
        var dropAck = 0
        var effects = 0
        val packets = mutableListOf<ReliablePacket>()
        val acks = mutableListOf<ReliableAck>()
        val sender = ReliableDelivery({ now }, { packets += it }, {}, { CompletableFuture.completedFuture(ReliableResult()) })
        val receiver =
            ReliableDelivery({ now }, {}, { acks += it }, {
                effects++
                CompletableFuture.completedFuture(ReliableResult(listOf("NEW")))
            })

        init {
            sender.connect("session")
            receiver.connect("session")
        }

        fun flush() {
            while (packets.isNotEmpty()) {
                val p = packets.removeAt(0)
                if (dropData-- <= 0) receiver.receive(p)
            }
            while (acks.isNotEmpty()) {
                val a = acks.removeAt(0)
                if (dropAck-- <= 0) sender.acknowledge(a)
            }
        }

        fun retry() {
            now += 2_000
            sender.tick()
            flush()
        }
    }

    @Test fun lostBeginAndAckReturnOriginalDecisionWithOneEffect() {
        val link = Link()
        link.dropData = 1
        link.dropAck = 1
        val result = link.sender.submit(1, listOf("source"))
        link.flush()
        assertFalse(result.isDone)
        link.retry()
        assertFalse(result.isDone)
        link.retry()
        assertEquals(listOf("NEW"), result.get().fields)
        assertEquals(1, link.effects)
    }

    @Test fun queueKeepsEndBehindAcknowledgedBeginAndReorderedDuplicatesDoNotReapply() {
        val link = Link()
        val start = link.sender.submit(1)
        val old = link.packets.single()
        val end = link.sender.submit(2)
        assertEquals(1, link.packets.size)
        link.flush()
        assertTrue(start.isDone)
        link.receiver.receive(old)
        link.flush()
        assertTrue(end.isDone)
        assertEquals(2, link.effects)
    }

    @Test fun statusCoalescesOnlyBeforeSequenceAssignment() {
        val link = Link()
        val assigned = link.sender.submit(200, listOf("first"), "status")
        val replaced = link.sender.submit(200, listOf("obsolete"), "status")
        val latest = link.sender.submit(200, listOf("latest"), "status")
        assertTrue(replaced.isCompletedExceptionally)
        assertEquals(listOf("first"), link.packets.single().fields)
        link.flush()
        assertTrue(assigned.isDone)
        assertEquals(listOf("latest"), link.packets.single().fields)
        link.flush()
        assertTrue(latest.isDone)
        assertEquals(2, link.effects)
    }

    @Test fun sameSessionReconnectRetainsPendingAndNewSessionRejectsOldPackets() {
        val link = Link()
        val pending = link.sender.submit(1)
        val old = link.packets.removeAt(0)
        link.sender.connect("session")
        link.retry()
        assertTrue(pending.isDone)
        link.receiver.connect("new")
        link.receiver.receive(old)
        assertEquals(1, link.effects)
    }

    @Test fun receiverDoesNotRunDuplicateWhileAsyncBusinessOperationWaits() {
        var effects = 0
        val completion = CompletableFuture<ReliableResult>()
        val acks = mutableListOf<ReliableAck>()
        val receiver =
            ReliableDelivery({ 0L }, {}, { acks += it }, {
                effects++
                completion
            })
        receiver.connect("s")
        val p = ReliablePacket("s", 1, 1, emptyList())
        repeat(4) { receiver.receive(p) }
        assertEquals(1, effects)
        assertTrue(acks.isEmpty())
        completion.complete(ReliableResult(listOf("DONE")))
        receiver.receive(p)
        assertEquals(2, acks.size)
        assertEquals(listOf("DONE"), acks.last().result.fields)
    }

    @Test fun obsoleteSessionCompletionCannotBecomeNewSessionAck() {
        val completion = CompletableFuture<ReliableResult>()
        val acks = mutableListOf<ReliableAck>()
        var closed = false
        val receiver = ReliableDelivery({ 0L }, {}, { acks += it }, { completion })
        receiver.connect("old")
        receiver.receive(ReliablePacket("old", 1, 1, emptyList()))
        receiver.connect("new")
        completion.complete(ReliableResult(attachment = Closeable { closed = true }))
        assertTrue(closed)
        assertTrue(acks.isEmpty())
    }

    @Test fun cachedDescriptorReplyIsReleasedOnNextAcceptedCommand() {
        var closed = 0
        val receiver =
            ReliableDelivery({ 0L }, {}, {}, {
                CompletableFuture.completedFuture(ReliableResult(attachment = Closeable { closed++ }))
            })
        receiver.connect("s")
        receiver.receive(ReliablePacket("s", 1, 1, emptyList()))
        receiver.receive(ReliablePacket("s", 1, 1, emptyList()))
        assertEquals(0, closed)
        receiver.receive(ReliablePacket("s", 2, 1, emptyList()))
        assertEquals(1, closed)
        receiver.close()
        assertEquals(2, closed)
    }

    @Test fun packetLossWithoutProcessDeathCannotCreateCrashSuspicion() {
        val journal = ScanAttemptJournal()
        val source = listOf("binding", "mtime", "size", "L1", "", "")
        var now = 0L
        val replies = mutableListOf<ReliableAck>()
        val receiver =
            ReliableDelivery({ now }, {}, { replies += it }, {
                val decision = journal.begin(ScanAttempt("indexer", "attempt", source, ScanAttemptMode.NORMAL))
                CompletableFuture.completedFuture(ReliableResult(listOf(decision.name)))
            })
        receiver.connect("s")
        val packet = ReliablePacket("s", 1, 1, emptyList())
        repeat(10) {
            receiver.receive(packet)
            now += 1_000
        }
        assertTrue(replies.all { it.result.fields == listOf("NORMAL") })
        assertTrue(journal.recover().isEmpty())
    }

    @Test fun onlySecondDeadIsolatedAttemptQuarantinesMatchingSourceAndStage() {
        val journal = ScanAttemptJournal()
        val source = listOf("binding", "mtime", "size", "L1", "", "")
        journal.begin(ScanAttempt("one", "a", source, ScanAttemptMode.NORMAL))
        journal.ownerDied("one")
        assertEquals(SourceRecovery.ISOLATE, journal.begin(ScanAttempt("two", "b", source, ScanAttemptMode.NORMAL)))
        journal.begin(ScanAttempt("two", "b", source, ScanAttemptMode.ISOLATED))
        journal.finish("one", "a")
        assertTrue(journal.recover().isEmpty())
        journal.ownerDied("two")
        assertEquals(SourceRecovery.QUARANTINE, journal.begin(ScanAttempt("three", "c", source, ScanAttemptMode.ISOLATED)))
        assertEquals(
            SourceRecovery.NORMAL,
            journal.begin(
                ScanAttempt(
                    "three",
                    "d",
                    source.toMutableList().also {
                        it[1] = "changed"
                    },
                    ScanAttemptMode.NORMAL,
                ),
            ),
        )
    }

    @Test fun delayedEndCannotRemoveNewAttempt() {
        val journal = ScanAttemptJournal()
        val source = listOf("binding", "mtime", "size", "L1", "", "")
        journal.begin(ScanAttempt("one", "a", source, ScanAttemptMode.NORMAL))
        journal.finish("one", "a")
        journal.begin(ScanAttempt("one", "b", source, ScanAttemptMode.NORMAL))
        journal.finish("one", "a")
        journal.ownerDied("one")
        assertEquals("b", journal.recover().single().reference)
    }

    @Test fun acceptedPacketOwnsFieldsUntilAsyncCompletionAndDuplicateReplay() {
        val fields = mutableListOf("original")
        val completion = CompletableFuture<ReliableResult>()
        val acks = mutableListOf<ReliableAck>()
        var accepted: ReliablePacket? = null
        var effects = 0
        ReliableDelivery({ 0L }, {}, { acks += it }, {
            accepted = it
            effects++
            completion
        }).use { receiver ->
            receiver.connect("s")
            receiver.receive(ReliablePacket("s", 1, 1, fields))
            fields[0] = "reused input buffer"
            assertEquals(listOf("original"), accepted?.fields)
            completion.complete(ReliableResult(listOf("DONE")))
            receiver.receive(ReliablePacket("s", 1, 1, listOf("original")))
            assertEquals(1, effects)
            assertEquals(2, acks.size)
            assertTrue(acks.all { it.result.fields == listOf("DONE") })
        }
    }
}
