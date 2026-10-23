package link.roadio.riff.ui

import link.roadio.riff.config.ProjectLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BrowseNamesTest {
    @Test
    fun everyConfiguredArticleIsCaseInsensitiveAndRequiresAWholeWord() {
        assertEquals(setOf("en", "es", "de", "ru", "pt"), ProjectLanguages.genreLocales)
        assertTrue(ProjectLanguages.leadingArticles.containsAll(listOf("el", "la", "lo", "las", "los", "die", "uma")))
        ProjectLanguages.leadingArticles.forEach { article ->
            assertEquals(article, "Echo", browseComparisonName("$article Echo", true))
            assertEquals(article, "Echo", browseComparisonName("  ${article.uppercase(Locale.ROOT)}\t\u00a0Echo", true))
            listOf(article, "$article ", "${article}atre", "$article-Echo", "$article.Echo").forEach { name ->
                assertEquals(name, name, browseComparisonName(name, true))
            }
            assertEquals("$article Echo", browseComparisonName("$article Echo", false))
        }
        assertEquals("Hard", browseComparisonName("Die Hard", true))
        assertEquals("the Echo", browseComparisonName("The the Echo", true))
        listOf("", " ", "Theatre", "Loser", "  Echo", "Моя музыка").forEach { name ->
            assertEquals(name, browseComparisonName(name, true))
        }
    }

    @Test
    fun labelContainersSortButAlbumTracksPlaylistOccurrencesAndFolderFilesKeepOrder() {
        val tracks = listOf(row("t2", "The Zebra", BrowserKind.TRACK), row("t1", "An Apple", BrowserKind.TRACK))
        listOf(BrowserKind.CATEGORY, BrowserKind.ARTIST, BrowserKind.GENRE).forEach { kind ->
            val source = row("container", "Container", kind, listOf(row("2", "Zebra"), row("1", "The Apple")))
            val enabled = source.withBrowseNamePolicy(true)
            assertEquals(listOf("1", "2"), enabled.children.map { it.id })
            assertEquals(listOf("2", "1"), source.children.map { it.id })
            assertEquals("The Apple", enabled.children.first().label)
            assertEquals(listOf("1", "2"), enabled.withBrowseNamePolicy(false).children.map { it.id })
        }
        listOf(BrowserKind.ALBUM, BrowserKind.PLAYLIST, BrowserKind.ROOT).forEach { kind ->
            val source = row("container", "Container", kind, tracks)
            assertEquals(listOf("t2", "t1"), source.withBrowseNamePolicy(true).children.map { it.id })
        }
        val folder =
            row(
                "f",
                "Folder",
                BrowserKind.FOLDER,
                listOf(row("f2", "Zebra", BrowserKind.FOLDER), row("f1", "Las Apples", BrowserKind.FOLDER)) + tracks,
            )
        val projected = folder.withBrowseNamePolicy(true)
        assertEquals(listOf("f1", "f2", "t2", "t1"), projected.children.map { it.id })
        assertEquals(tracks.map { it.label }, projected.children.takeLast(2).map { it.label })
        val folderCategory = folder.copy(id = HierarchyRoot.FOLDERS.categoryId, kind = BrowserKind.CATEGORY)
        assertEquals(listOf("f1", "f2", "t2", "t1"), folderCategory.withBrowseNamePolicy(true).children.map { it.id })
    }

    @Test
    fun disablingPolicyRestoresFullNameOrderAndInvalidatesPreparedSearch() {
        val source =
            row(
                "c",
                "Container",
                BrowserKind.CATEGORY,
                listOf(row("the", "The Beatles"), row("echo", "Echo"), row("die", "Die Hard")),
            )
        val yes = source.withBrowseNamePolicy(true).children
        val no = source.withBrowseNamePolicy(false).children
        assertEquals(listOf("the", "echo", "die"), yes.map { it.id })
        assertEquals(listOf("die", "echo", "the"), no.map { it.id })
        assertNotSame(yes, no)
        val group = listOf(AzGroup.LATIN_AC)
        val yesQuery = AzSearchQuery(yes, group, emptyList())
        val noQuery = AzSearchQuery(no, group, emptyList())
        assertFalse(yesQuery.sameAs(noQuery))
        val snapshot = AzPreparedSection(yes).snapshot(yesQuery)
        assertEquals(listOf("the"), snapshot.visibleItems.map { it.id })
        assertEquals(filterAzItems(yes, group), snapshot.visibleItems)
        assertEquals(azGroupResultCounts(yes, emptyList()), snapshot.resultCounts)
        assertEquals(1, snapshot.resultCounts[AzGroup.LATIN_AC])
        assertTrue(AzPreparedSection(no).snapshot(noQuery).visibleItems.isEmpty())
        val orders = AzReloadOrders(listOf(yes to group, no to group))
        assertEquals(listOf("the"), orders.order(yes, group))
        assertEquals(emptyList<String>(), orders.order(no, group))
    }

    @Test
    fun equalComparisonNamesHaveDeterministicOriginalLabelThenIdentityTies() {
        val source =
            row(
                "c",
                "Container",
                BrowserKind.CATEGORY,
                listOf(row("z", "The Echo"), row("b", "Echo"), row("a", "Echo")),
            )
        assertEquals(listOf("a", "b", "z"), source.withBrowseNamePolicy(true).children.map { it.id })
        val genres =
            source.copy(
                children =
                    listOf(
                        row("genre:10", "Echo", BrowserKind.GENRE).copy(genreId = 10),
                        row("genre:2", "Echo", BrowserKind.GENRE).copy(genreId = 2),
                    ),
            )
        assertEquals(listOf(2L, 10L), genres.withBrowseNamePolicy(true).children.map { it.genreId })
    }

    @Test
    fun technicalDecorationPreservesOrderingSearchCountsAndFullNameTies() {
        val original =
            listOf(
                row("s", "[[SINGLES]]"),
                row("personal", "The Echo: singles"),
                row("ordinary", "Echo: singles"),
                row("z", "Zebra"),
            )
        val decorated =
            original.map {
                when (it.id) {
                    "s" -> it.copy(label = "◇ Singles", comparisonNameOverride = it.label)
                    "personal" -> it.copy(label = "The Echo ◇ Singles", comparisonNameOverride = it.label)
                    else -> it
                }
            }
        listOf(false, true).forEach { policy ->
            val baseline = row("c", "Albums", BrowserKind.CATEGORY, original).withBrowseNamePolicy(policy).children
            val display = row("c", "Albums", BrowserKind.CATEGORY, decorated).withBrowseNamePolicy(policy).children
            assertEquals(baseline.map { it.id }, display.map { it.id })
            assertEquals(baseline.map { it.comparisonLabel }, display.map { it.comparisonLabel })
            AzGroup.entries.forEach { group ->
                assertEquals(filterAzItems(baseline, listOf(group)).map { it.id }, filterAzItems(display, listOf(group)).map { it.id })
            }
            assertEquals(azGroupResultCounts(baseline, emptyList()), azGroupResultCounts(display, emptyList()))
        }
        assertEquals("Renamed", row("plain", "Old").copy(label = "Renamed").comparisonLabel)
    }

    private fun row(
        id: String,
        label: String,
        kind: BrowserKind = BrowserKind.ALBUM,
        children: List<BrowserItem> = emptyList(),
    ) = BrowserItem(id, label, kind, children = children)
}
