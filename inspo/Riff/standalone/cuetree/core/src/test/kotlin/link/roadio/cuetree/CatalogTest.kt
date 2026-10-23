// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    @Test fun externalCueCreatesOrderedAlbumWithoutAlbumTag() {
        val p = MemoryProvider()
        p.file("disc.wav", ByteArray(32) { it.toByte() })
        p.file(
            "disc.cue",
            """
            FILE "disc.wav" WAVE
              TRACK 01 AUDIO
                TITLE "First"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second"
                INDEX 01 00:10:00
            """.trimIndent().toByteArray(),
        )
        val indexer = Indexer(MockMediaID(), 1000, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val album = first.albums.single { it.kind == AlbumKind.EXPLICIT }
        assertEquals(listOf("First", "Second"), album.sources.map { first.nodes[it].name })
        assertEquals(listOf(0L, 10_000_000L), album.sources.map { first.nodes[it].span!!.beginUs })
        assertEquals(
            0,
            first.albums
                .single { it.kind == AlbumKind.SINGLES }
                .tracks.size,
        )
        val second = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertEquals(album.id, second.albums.single { it.kind == AlbumKind.EXPLICIT }.id)
        assertEquals(first.nodes.map { it.mediaId }, second.nodes.map { it.mediaId })
        val resolved = SourceResolver(listOf(p)).resolve(first, album.sources[1])
        assertEquals(10_000_000L, resolved.span!!.beginUs)
        p.denied += p.rootHandle
        val lost = indexer.scan(listOf(p.rootSpec()), second, 3)
        assertEquals(album.id, lost.albums.single { it.kind == AlbumKind.EXPLICIT }.id)
        assertTrue(lost.nodes.all { !it.life.available })
        p.denied.clear()
        val back = indexer.scan(listOf(p.rootSpec()), lost, 4)
        assertTrue(back.nodes.all { it.life.available && it.life.generation == back.generation })
    }

    @Test fun invalidAndSingleTrackCueDoNotCreateAlbums() {
        for (text in listOf("TRACK 01 AUDIO", "FILE \"disc.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00")) {
            val p = MemoryProvider()
            p.file("disc.wav", ByteArray(32))
            p.file("disc.cue", text.toByteArray())
            val result = Indexer(MockMediaID(), 1000, listOf(p)).scan(listOf(p.rootSpec()), generation = 1)
            assertTrue(result.albums.none { it.kind == AlbumKind.EXPLICIT })
            assertEquals(
                1,
                result.albums
                    .single()
                    .tracks.size,
            )
        }
    }

    @Test fun cueCannotEscapeRoot() {
        val p = MemoryProvider()
        p.file("disc.wav", ByteArray(32))
        p.file("disc.cue", "FILE \"../disc.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nINDEX 01 00:01:00".toByteArray())
        val result = Indexer(MockMediaID(), 1000, listOf(p)).scan(listOf(p.rootSpec()), generation = 1)
        assertTrue(result.nodes.none { it.kind == NodeKind.FRAGMENT })
        assertTrue(result.diagnostics.any { it.code == "STRUCTURE_ACCESS" })
    }

    @Test fun compilationCompletenessPrecedesArtistDisagreementAndGenresLinkWholeAlbum() {
        val p = MemoryProvider()
        p.file("a.mp3")
        p.file("b.mp3")
        val common = mapOf("album" to listOf("Compilation"), "trackTotal" to listOf("2"))
        val reader =
            metadataByName(
                mapOf(
                    "a.mp3" to common + mapOf("trackNumber" to listOf("1"), "artist" to listOf("A"), "genre" to listOf("Rock")),
                    "b.mp3" to common + mapOf("trackNumber" to listOf("2"), "artist" to listOf("B"), "genre" to listOf("Other")),
                ),
            )
        val indexer = Indexer(MockMediaID(), 1000, listOf(p), reader, GenreResolver { if (it == "Rock") GenreRef(dictionaryId = 12) else GenreRef(text = it) })
        val result = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val album = result.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals(2, album.tracks.size)
        assertEquals(setOf("A", "B"), result.artists.map { it.name }.toSet())
        assertTrue(result.artists.all { it.albums == listOf(album.id) })
        assertTrue(result.genres.all { it.albums == listOf(album.id) })
        assertEquals(2, result.genres.size)
        val next = indexer.scan(listOf(p.rootSpec()), result, 2)
        assertEquals(result.albums.map { it.id }, next.albums.map { it.id })
        assertEquals(result.artists.map { it.id }, next.artists.map { it.id })
    }

    @Test fun numericDiscFamiliesAreGroupedBeforeLocalCompleteDiscs() {
        val p = MemoryProvider()
        val d9 = p.directory("CD9")
        val d10 = p.directory("CD10")
        p.file("nine.mp3", parent = d9)
        p.file("ten.mp3", parent = d10)
        val common = mapOf("album" to listOf("Set"), "trackNumber" to listOf("1"), "trackTotal" to listOf("1"), "discTotal" to listOf("10"))
        val result =
            Indexer(
                MockMediaID(),
                1000,
                listOf(p),
                metadataByName(
                    mapOf(
                        "nine.mp3" to common + ("discNumber" to listOf("9")),
                        "ten.mp3" to common + ("discNumber" to listOf("10")),
                    ),
                ),
            ).scan(listOf(p.rootSpec()), generation = 1)
        val album = result.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals(listOf("nine.mp3", "ten.mp3"), album.sources.map { result.nodes[it].name })
    }

    @Test fun singlesIsOneStableTypedAlbumAndArtistWaitsForAllAlbums() {
        val p = MemoryProvider()
        val a = p.file("a.mp3")
        val b = p.file("b.mp3")
        val reader =
            metadataByName(
                mapOf(
                    "a.mp3" to mapOf("artist" to listOf("Artist"), "genre" to listOf("One")),
                    "b.mp3" to mapOf("artist" to listOf("Artist"), "genre" to listOf("Two")),
                ),
            )
        val indexer = Indexer(MockMediaID(), 1000, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(1, first.albums.size)
        assertEquals(2, first.albums[0].tracks.size)
        p.denied += a
        val partial = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertTrue(
            partial.artists
                .single()
                .life.available,
        )
        assertTrue(
            partial.albums
                .single()
                .life.available,
        )
        p.denied += b
        val offline = indexer.scan(listOf(p.rootSpec()), partial, 3)
        assertEquals(
            3L,
            offline.albums
                .single()
                .life.generation,
        )
        assertEquals(
            3L,
            offline.artists
                .single()
                .life.generation,
        )
        assertEquals(first.albums.single().id, offline.albums.single().id)
    }
}
