package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzLivePreviewTest {
    private val rows =
        listOf("Zulu", "Alpha", "Beta", "Яблоко").map {
            BrowserItem(it, it, BrowserKind.ALBUM)
        }
    private val root = BrowserItem("root", "Albums", BrowserKind.ALBUM, children = rows)

    private fun state(
        applied: List<AzGroup> = emptyList(),
        draft: List<AzGroup> = emptyList(),
    ): PlayerUiState {
        val query = AzSearchQuery(rows, applied, draft)
        return PlayerUiState(
            screen = PlayerScreen.AZ_SEARCH,
            root = root,
            hierarchyRoot = HierarchyRoot.ALBUMS,
            azSteps = applied,
            azDraftSteps = draft,
            azSnapshot = AzPreparedSection(rows).snapshot(query),
            selectorIndex = 2,
            selectorOffset = 17,
            focusedItemId = "Beta",
            nowPlaying = NowPlayingUiState(),
        )
    }

    @Test
    fun draftPreviewNarrowsAndExpandsWithoutImplicitCommit() {
        val original = state()
        val narrowed = state(draft = listOf(AzGroup.LATIN_AC))
        assertEquals(listOf("Alpha", "Beta"), narrowed.visibleChildren().map { it.label })
        assertEquals(emptyList<AzGroup>(), narrowed.azSteps)
        assertEquals(2, narrowed.selectorIndex)
        assertEquals(17, narrowed.selectorOffset)
        val cleared = narrowed.copy(azDraftSteps = emptyList())
        assertEquals(rows, cleared.visibleChildren())
        assertEquals(rows, original.visibleChildren())
        assertEquals(emptyList<AzGroup>(), cleared.azDisplayedSteps())
    }

    @Test
    fun pendingResultKeepsLastCompleteRowsAndDisplayedQuery() {
        val applied = state(draft = listOf(AzGroup.LATIN_AC))
        val pending = applied.copy(azDraftSteps = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_JL))
        assertEquals(null, pending.currentAzSnapshot())
        assertEquals(applied.visibleChildren(), pending.visibleChildren())
        assertEquals(applied.azDisplayedSteps(), pending.azDisplayedSteps())
        val completed = pending.copy(azSnapshot = AzPreparedSection(rows).snapshot(pending.azQuery()))
        assertEquals(listOf("Alpha"), completed.visibleChildren().map { it.label })
        val backspacePending = completed.copy(azDraftSteps = listOf(AzGroup.LATIN_AC))
        assertEquals(completed.visibleChildren(), backspacePending.visibleChildren())
        val backspaceReady = backspacePending.copy(azSnapshot = AzPreparedSection(rows).snapshot(backspacePending.azQuery()))
        assertEquals(listOf("Alpha", "Beta"), backspaceReady.visibleChildren().map { it.label })
    }

    @Test
    fun cancelRestoresAppliedRowsAndOriginalNonzeroPositionImmediately() {
        val preview = state(applied = listOf(AzGroup.LATIN_AC), draft = listOf(AzGroup.LATIN_WZ))
        assertEquals(listOf("Zulu"), preview.visibleChildren().map { it.label })
        val cancelled = preview.copy(screen = PlayerScreen.HIERARCHY, azDraftSteps = null)
        assertEquals(listOf("Alpha", "Beta"), cancelled.visibleChildren().map { it.label })
        assertTrue(cancelled.azAppliedReady())
        assertEquals(preview.selectorIndex, cancelled.selectorIndex)
        assertEquals(preview.selectorOffset, cancelled.selectorOffset)
        assertEquals(preview.focusedItemId, cancelled.focusedItemId)
        assertEquals(listOf(AzGroup.LATIN_AC), cancelled.azDisplayedSteps())
    }

    @Test
    fun staleSourceCannotLeakOldPreviewIntoNewContainer() {
        val original = state(draft = listOf(AzGroup.LATIN_AC))
        val next = listOf(BrowserItem("new", "New source", BrowserKind.ALBUM))
        val changed = original.copy(root = root.copy(children = next))
        assertEquals(null, changed.azDisplaySnapshot())
        assertEquals(next, changed.visibleChildren())
        assertFalse(changed.visibleChildren().any { it.label == "Alpha" })
    }

    @Test
    fun emptyAvailabilityKeepsOnlyThePhysicalBackspaceRow() {
        assertEquals(listOf(AZ_NUMERIC_KEYS), azGridRows(true, emptySet()))
        assertEquals(
            listOf(listOf(AzKey.Group(AzGroup.DIGIT_67), AzKey.Group(AzGroup.DIGIT_89), AzKey.Backspace)),
            azGridRows(false, emptySet()),
        )
    }

    @Test
    fun narrowRowsHideIndependentlyWithoutRepacking() {
        val finalRow = listOf(AzKey.Group(AzGroup.DIGIT_67), AzKey.Group(AzGroup.DIGIT_89), AzKey.Backspace)
        assertEquals(
            listOf(AZ_LATIN_GROUPS.take(4).map(AzKey::Group), finalRow),
            azGridRows(false, setOf(AzGroup.LATIN_DF)),
        )
        assertEquals(
            listOf(AZ_CYRILLIC_GROUPS.drop(4).map(AzKey::Group), finalRow),
            azGridRows(false, setOf(AzGroup.CYRILLIC_RU)),
        )
        assertEquals(
            listOf(
                AZ_CYRILLIC_GROUPS.drop(4).map(AzKey::Group),
                AZ_LATIN_GROUPS.take(4).map(AzKey::Group),
                finalRow,
            ),
            azGridRows(false, setOf(AzGroup.LATIN_DF, AzGroup.CYRILLIC_RU)),
        )
    }

    @Test
    fun upperNarrowNumericRowDependsOnItsOwnCandidates() {
        val numeric = azNumericRows(false)
        listOf(AzGroup.SYMBOL, AzGroup.DIGIT_01, AzGroup.DIGIT_23, AzGroup.DIGIT_45).forEach { group ->
            assertEquals(numeric, azGridRows(false, setOf(group)))
        }
        listOf(AzGroup.DIGIT_67, AzGroup.DIGIT_89).forEach { group ->
            assertEquals(listOf(numeric.last()), azGridRows(false, setOf(group)))
        }
    }

    @Test
    fun blocksAppearFromTopAsCyrillicLatinAndNumericInBothForms() {
        listOf(true, false).forEach { wide ->
            assertEquals(
                AZ_CYRILLIC_GROUPS.map(AzKey::Group) + AZ_LATIN_GROUPS.map(AzKey::Group) + AZ_NUMERIC_KEYS,
                azGridRows(wide).flatten(),
            )
            assertEquals(
                listOf(AZ_LATIN_GROUPS.map(AzKey::Group), AZ_NUMERIC_KEYS),
                azGridRows(true, setOf(AzGroup.LATIN_DF)),
            )
            assertEquals(
                listOf(AZ_CYRILLIC_GROUPS.map(AzKey::Group), AZ_NUMERIC_KEYS),
                azGridRows(true, setOf(AzGroup.CYRILLIC_DZ)),
            )
        }
    }

    @Test
    fun eachCandidateKeepsItsOriginalPhysicalSlotsAndOneFinalBackspace() {
        listOf(true, false).forEach { wide ->
            val complete = azGridRows(wide)
            AzGroup.entries.forEach { group ->
                val candidateRow = complete.single { AzKey.Group(group) in it }
                val finalRow = complete.last()
                val expected = if (candidateRow == finalRow) listOf(finalRow) else listOf(candidateRow, finalRow)
                assertEquals(expected, azGridRows(wide, setOf(group)))
            }
        }
    }

    @Test
    fun allGroupsKeepFixedColumnSpansInBothResponsiveForms() {
        val wide = azGridRows(true)
        val narrow = azGridRows(false)
        assertEquals(listOf(8, 8, 7), wide.map { it.size })
        assertEquals(listOf(4, 4, 4, 4, 4, 3), narrow.map { it.size })
        assertEquals(
            AzGroup.entries.toSet(),
            wide
                .flatten()
                .filterIsInstance<AzKey.Group>()
                .map { it.group }
                .toSet(),
        )
        assertEquals(
            AzGroup.entries.toSet(),
            narrow
                .flatten()
                .filterIsInstance<AzKey.Group>()
                .map { it.group }
                .toSet(),
        )
        assertEquals(1, wide.flatten().count { it == AzKey.Backspace })
        assertEquals(1, narrow.flatten().count { it == AzKey.Backspace })
    }
}
