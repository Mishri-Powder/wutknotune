// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class SinglesTest {
    private class Library(
        vocabulary: CatalogVocabulary = testVocabulary,
        genreResolver: GenreResolver = testGenreResolver,
    ) {
        val provider = MemoryProvider()
        val metadata = mutableMapOf<String, Attributes>()
        val durations = mutableMapOf<String, Long>()
        val indexer =
            Indexer(
                MockMediaID(),
                10,
                listOf(provider),
                MetadataReader { input, _, _ ->
                    Observation(metadata[input.id].orEmpty(), emptyList(), emptyList(), sourceEvidence = durations[input.id]?.let { mapOf(SOURCE_DURATION_US to listOf(it.toString())) }.orEmpty())
                },
                genres = genreResolver,
                vocabulary = vocabulary,
            )

        fun release(
            album: String,
            titles: List<String>,
            artist: String = "Band",
            duration: Long? = 180_000_000,
            extra: Attributes = emptyMap(),
        ): List<MemoryProvider.Handle> {
            val folder = provider.directory(album)
            return titles.mapIndexed { index, title ->
                val name = "$album-${index + 1}.flac"
                metadata[name] = mapOf("album" to listOf(album), "albumArtist" to listOf(artist), "artist" to listOf(artist), "title" to listOf(title), "trackNumber" to listOf("${index + 1}"), "trackTotal" to listOf("${titles.size}")) + extra
                if (duration != null) durations[name] = duration
                provider.file(name, parent = folder)
            }
        }

        fun scan(
            previous: Snapshot? = null,
            generation: Long = 1,
            book: Boolean = false,
        ): Snapshot = indexer.scan(listOf(provider.rootSpec().copy(book = book)), previous ?: Snapshot.empty(MockMediaID().scheme), generation)
    }

    private fun Snapshot.live() = albums.filter { it.sources.isNotEmpty() }

    @Test fun threeReleasesQualifyWithoutAnyFullAlbumAndKeepBasketId() {
        val l = Library()
        repeat(3) { l.release("Single $it", listOf("Work $it")) }
        val first = l.scan()
        val basket = first.live().single()
        assertEquals(AlbumKind.ARTIST_SINGLES, basket.kind)
        assertEquals("Band: singles", basket.title)
        assertEquals(3, basket.sources.size)
        assertTrue(
            first.albums
                .single { it.kind == AlbumKind.SINGLES }
                .sources
                .isEmpty(),
        )
        val next = l.scan(first, 2)
        assertEquals(basket.id, next.live().single().id)
        assertEquals(next, FlatCatalogs.encode(next).snapshot())
    }

    @Test fun shortFragmentsUseGlobalUntilAnIndependentCompleteAlbumQualifies() {
        val l = Library()
        l.release("Fragment", listOf("One", "Two"), extra = mapOf("trackNumber" to listOf("9"), "trackTotal" to listOf("12")))
        val initial = l.scan()
        assertTrue(initial.live().all { it.kind == AlbumKind.SINGLES })
        l.release("Studio", (1..12).map { "Studio $it" })
        val next = l.scan(initial, 2)
        assertEquals(
            12,
            next
                .live()
                .single { it.kind == AlbumKind.ORDINARY }
                .sources.size,
        )
        assertEquals(
            2,
            next
                .live()
                .single { it.kind == AlbumKind.ARTIST_SINGLES }
                .sources.size,
        )
        assertEquals(14, next.live().flatMap { it.sources }.size)
    }

    @Test fun addedTracksPromoteGroupWithoutReusingSyntheticId() {
        val l = Library()
        repeat(3) { l.release("Short $it", listOf("Work $it")) }
        val first = l.scan()
        val basket = first.live().single().id
        val folder =
            l.provider.items.entries
                .single { it.value.name == "Short 0" }
                .key
        for (n in 2..12) {
            val name = "Short 0-$n.flac"
            l.provider.file(name, parent = folder)
            l.metadata[name] = l.metadata.getValue("Short 0-1.flac") + mapOf("title" to listOf("Work $n"), "trackNumber" to listOf("$n"), "trackTotal" to listOf("12"))
            l.durations[name] = 180_000_000
        }
        l.metadata["Short 0-1.flac"] = l.metadata.getValue("Short 0-1.flac") + ("trackTotal" to listOf("12"))
        val firstFile =
            l.provider.items.entries
                .single { it.value.name == "Short 0-1.flac" }
                .value
        firstFile.modified++
        val next = l.scan(first, 2)
        val ordinary = next.live().single { it.kind == AlbumKind.ORDINARY }
        assertEquals(12, ordinary.sources.size)
        assertNotEquals(basket, ordinary.id)
        assertEquals(basket, next.live().single { it.kind == AlbumKind.ARTIST_SINGLES }.id)
    }

    @Test fun missingMembersPreventDemotionUntilExpiryIncludingProjectionOnlyExpiry() {
        val l = Library()
        val handles = l.release("Studio", (1..12).map { "Studio $it" })
        val first = l.scan()
        handles.drop(2).forEach { l.provider.items.remove(it) }
        val retained = l.scan(first, 2)
        assertEquals(
            12,
            retained
                .live()
                .single()
                .sources.size,
        )
        assertEquals(AlbumKind.ORDINARY, retained.live().single().kind)
        val expired = retained.advancePublication(11, 10, vocabulary = testVocabulary)
        assertEquals(
            2,
            expired
                .live()
                .single()
                .sources.size,
        )
        assertEquals(AlbumKind.SINGLES, expired.live().single().kind)
        assertTrue(
            expired
                .live()
                .single()
                .sources
                .all { expired.nodes[it].life.available },
        )
    }

    @Test fun offlineAnchorStillQualifiesAndTemporaryAbsenceKeepsBasketIdentity() {
        val l = Library()
        val studio = l.release("Studio", (1..8).map { "Studio $it" })
        l.release("Short", listOf("Work"))
        val first = l.scan()
        val id = first.live().single { it.kind == AlbumKind.ARTIST_SINGLES }.id
        studio.forEach { l.provider.denied += it }
        val next = l.scan(first, 2)
        assertEquals(id, next.live().single { it.kind == AlbumKind.ARTIST_SINGLES }.id)
        assertEquals(
            8,
            next
                .live()
                .single { it.kind == AlbumKind.ORDINARY }
                .sources.size,
        )
    }

    @Test fun unknownOrLongDurationDoesNotTurnThreeTrackAlbumIntoSingles() {
        for (duration in listOf(null, 600_000_000L, 1_200_000_000L)) {
            val l = Library()
            l.release("Long", listOf("One", "Two", "Three"), duration = duration)
            assertEquals(
                AlbumKind.ORDINARY,
                l
                    .scan()
                    .live()
                    .single()
                    .kind,
            )
        }
    }

    @Test fun versionBundlesRecognizeShortWorkNamesWithoutRemovingPartNumbers() {
        val l = Library()
        l.release("Go Remixes", listOf("Go", "Go (Radio Edit)", "Go - Extended Mix", "Go (Club Remix)"))
        assertEquals(
            AlbumKind.SINGLES,
            l
                .scan()
                .live()
                .single()
                .kind,
        )
        assertEquals("go" to true, SinglesClassifier.versionBase("Go (Radio Edit)", testVocabulary))
        assertEquals("dream - radio day" to true, SinglesClassifier.versionBase("Dream - Radio Day - Club Mix", testVocabulary))
        assertEquals("work" to true, SinglesClassifier.versionBase("Work - Edit - DJ Spinna Remix", testVocabulary))
        assertEquals("work (part 2)" to false, SinglesClassifier.versionBase("Work (Part 2)", testVocabulary))
        assertEquals("work - mix part 2" to false, SinglesClassifier.versionBase("Work - Mix Part 2", testVocabulary))
        val generic = Library()
        generic.release("Generic", listOf("Track 1", "Track 1 (Mix)", "Track 2", "Track 2 (Mix)"))
        assertEquals(
            AlbumKind.ORDINARY,
            generic
                .scan()
                .live()
                .single()
                .kind,
        )
    }

    @Test fun electronicLiveSetsQualifyWithoutArtistAllowlistButLiveAloneDoesNot() {
        val l = Library()
        repeat(3) { l.release("Event $it", (1..8).map { "Position $it" }, artist = "DJ Example", extra = mapOf("genre" to listOf("Electronic", "Live"))) }
        assertEquals(
            AlbumKind.ARTIST_SINGLES,
            l
                .scan()
                .live()
                .single()
                .kind,
        )
        val live = Library()
        live.release("Concert", (1..8).map { "Song $it" }, extra = mapOf("genre" to listOf("Live", "Reggae")))
        assertEquals(
            AlbumKind.ORDINARY,
            live
                .scan()
                .live()
                .single()
                .kind,
        )
        val explicit = Library()
        explicit.release("Evening DJ Set", (1..8).map { "Position $it" })
        assertEquals(
            AlbumKind.SINGLES,
            explicit
                .scan()
                .live()
                .single()
                .kind,
        )
    }

    @Test fun booksAndPlaceholderCreditsNeverCreatePersonalMusicBasket() {
        val l = Library()
        repeat(3) { l.release("Book $it", listOf("Chapter $it")) }
        assertTrue(l.scan(book = true).live().none { it.kind.isSingles })
        val p = Library()
        repeat(3) { p.release("Release $it", listOf("Work $it"), artist = "Various Artists") }
        assertEquals(
            AlbumKind.SINGLES,
            p
                .scan()
                .live()
                .single()
                .kind,
        )
        assertEquals(listOf("a, b"), SinglesClassifier.credit(listOf("A, B")))
        assertNotEquals(SinglesClassifier.credit(listOf("A, B")), SinglesClassifier.credit(listOf("A", "B")))
    }

    @Test fun mismatchedBracketsAreNotVersionDescriptors() {
        for (title in listOf("Work (Radio Edit]", "Work [Club Remix)", "Work - (Radio Edit]", "Work - [Club Remix)")) {
            assertEquals(canonical(title) to false, SinglesClassifier.versionBase(title, testVocabulary))
        }
    }

    @Test fun injectedLanguageChangesEvidenceWithoutBuiltInFallback() {
        val vocabulary = CatalogVocabulary(versionWords = setOf("改编"), protectedWords = setOf("章"))
        val l = Library(vocabulary)
        l.release("作品", listOf("歌", "歌 (改编)", "歌 - 改编", "歌 [改编]"))
        assertEquals(
            AlbumKind.SINGLES,
            l
                .scan()
                .live()
                .single()
                .kind,
        )
        assertEquals("song (radio edit)" to false, SinglesClassifier.versionBase("Song (Radio Edit)", vocabulary))
        assertEquals("歌 (改编 章 2)" to false, SinglesClassifier.versionBase("歌 (改编 章 2)", vocabulary))
    }

    @Test fun configuredGenericCreditCannotBecomeAnArtistBasket() {
        val vocabulary = CatalogVocabulary(genericArtists = setOf("Vários artistas"))
        val l = Library(vocabulary)
        repeat(3) { l.release("Release $it", listOf("Song $it"), artist = "VÁRIOS ARTISTAS") }
        assertTrue(l.scan().live().all { it.kind == AlbumKind.SINGLES })
        val neutral = Library(CatalogVocabulary.EMPTY)
        repeat(3) { neutral.release("Release $it", listOf("Song $it"), artist = "Various Artists") }
        assertEquals(
            AlbumKind.ARTIST_SINGLES,
            neutral
                .scan()
                .live()
                .single()
                .kind,
        )
    }

    @Test fun dictionaryIdentitySurvivesPersistenceAndExpiryWithoutTextResolver() {
        val vocabulary = CatalogVocabulary(liveGenreIds = setOf(101), electronicGenreIds = setOf(102))
        val l = Library(vocabulary, GenreResolver { GenreRef(text = it) })
        repeat(3) {
            l.release("Event $it", (1..8).map { "Position $it" }, extra = mapOf("genre" to listOf("任意"), "genreId" to listOf("101", "102")))
        }
        val expires = l.release("Other", listOf("Other"), artist = "Other").single()
        val initial = l.scan()
        l.provider.items.remove(expires)
        val retained = FlatCatalogs.encode(l.scan(initial, 2)).snapshot()
        val next = retained.advancePublication(11, 10, vocabulary = vocabulary)
        val live = next.live().single()
        assertEquals(AlbumKind.ARTIST_SINGLES, live.kind)
        assertEquals(24, live.sources.size)
    }

    @Test fun configuredTermsAreLiteralsAndCallerMutationCannotChangePolicy() {
        val words = mutableSetOf("e.dit")
        val vocabulary = CatalogVocabulary(versionWords = words)
        words.clear()
        assertEquals("song" to true, SinglesClassifier.versionBase("Song (e.dit)", vocabulary))
        assertEquals("song (exdit)" to false, SinglesClassifier.versionBase("Song (exdit)", vocabulary))
    }
}
