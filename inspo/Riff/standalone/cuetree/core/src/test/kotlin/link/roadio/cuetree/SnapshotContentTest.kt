// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class SnapshotContentTest {
    private fun snapshot(): Snapshot {
        val provider = MemoryProvider()
        provider.file("track.mp3")
        return Indexer(MockMediaID(), ttl = 10, providers = listOf(provider), metadata = emptyMetadata)
            .scan(listOf(provider.rootSpec()), generation = 1)
    }

    @Test fun onlyPublicationMarksAndDiagnosticsAreIgnored() {
        val first = snapshot()
        val next =
            first.copy(
                generation = 99,
                nodes = first.nodes.map { it.copy(life = it.life.copy(generation = 99)) },
                albums = first.albums.map { it.copy(life = it.life.copy(generation = 99)) },
                artists = first.artists.map { it.copy(life = it.life.copy(generation = 99)) },
                genres = first.genres.map { it.copy(life = it.life.copy(generation = 99)) },
                attributeCatalog = first.attributeCatalog.map { it.copy(life = it.life.copy(generation = 99)) },
                diagnostics = listOf(Diagnostic("test", "test", "transient")),
            )
        assertTrue(first.hasSameContentAs(next))
        assertTrue(next.hasSameContentAs(first))
        assertFalse(first.hasSameContentAs(first.copy(metadataRevision = "changed")))
        assertFalse(first.hasSameContentAs(first.copy(nodes = first.nodes.map { if (it.kind == NodeKind.MEDIA) it.copy(identityFlags = it.identityFlags or Flags.FAILEDL2) else it })))
        assertFalse(first.hasSameContentAs(first.copy(identityScheme = "other")))
        assertFalse(first.hasSameContentAs(first.copy(nodes = first.nodes.map { it.copy(life = it.life.copy(flags = Flags.ACSS)) })))
        assertFalse(first.hasSameContentAs(first.copy(nodes = first.nodes.map { it.copy(sourceEvidence = mapOf("changed" to listOf("value"))) })))
    }

    @Test fun occurrenceOrderAndRepeatedPositionsAreContent() {
        val first = snapshot()
        val album = first.albums.single()
        val source = first.nodes.indexOfFirst { it.kind == NodeKind.MEDIA }
        val repeated = first.copy(albums = listOf(album.copy(tracks = listOf(first.nodes[source].id, first.nodes[source].id), sources = listOf(source, source))))
        assertFalse(first.hasSameContentAs(repeated))
        assertFalse(first.hasSameContentAs(first.copy(nodes = first.nodes.reversed())))
    }
}
