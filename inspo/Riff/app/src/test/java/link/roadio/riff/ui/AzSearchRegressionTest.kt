package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class AzSearchRegressionTest {
    private fun item(label: String) = BrowserItem(label, label, BrowserKind.ALBUM)

    @Test
    fun expandedGroupsRetainEveryBasicAndHiddenMember() {
        val hidden =
            mapOf(
                AzGroup.LATIN_AC to listOf("Á", "Ä", "À", "Â", "Ã", "Å", "Ā", "Ă", "Ą", "Ç", "Ć", "Č", "Æ"),
                AzGroup.LATIN_DF to listOf("É", "È", "Ê", "Ë", "Ē", "Ď", "Đ", "Ð", "Ə"),
                AzGroup.LATIN_GI to listOf("Ğ", "Ģ", "Í", "Ì", "Î", "Ï", "Ī", "İ", "ı", "Ħ"),
                AzGroup.LATIN_JL to listOf("Ķ", "Ĺ", "Ľ", "Ł"),
                AzGroup.LATIN_MO to listOf("Ñ", "Ń", "Ň", "Ó", "Ò", "Ô", "Õ", "Ö", "Ō", "Œ", "Ø", "Ŋ"),
                AzGroup.LATIN_PS to listOf("Ŕ", "Ř", "Ś", "Š", "Ş", "Ș", "ß", "ẞ"),
                AzGroup.LATIN_TV to listOf("Ţ", "Ț", "Ť", "Ú", "Ù", "Û", "Ü", "Ū", "Þ"),
                AzGroup.LATIN_WZ to listOf("Ý", "Ÿ", "Ź", "Ż", "Ž"),
                AzGroup.CYRILLIC_AG to listOf("Ә", "Ӑ", "Ӓ", "Ґ", "Ғ", "Ѓ", "Ҕ"),
                AzGroup.CYRILLIC_DZ to listOf("Ё", "Ђ", "Є", "Ӗ", "Җ", "Ѕ", "Ҙ", "Џ"),
                AzGroup.CYRILLIC_IL to listOf("І", "Ї", "Ӣ", "Ј", "Қ", "Ҡ", "Ќ", "Љ"),
                AzGroup.CYRILLIC_MP to listOf("Ң", "Ҥ", "Њ", "Ө", "Ӧ"),
                AzGroup.CYRILLIC_RU to listOf("Ҫ", "Ћ", "Ў", "Ӯ", "Ү", "Ұ", "Ӳ", "Ӱ"),
                AzGroup.CYRILLIC_FCH to listOf("Ҳ", "Һ", "Ҷ", "Ҹ", "Ӌ"),
            )
        (AZ_LATIN_GROUPS + AZ_CYRILLIC_GROUPS).forEach { group ->
            val members = group.label.map(Char::toString) + hidden[group].orEmpty()
            members.flatMap { listOf(it, it.lowercase()) }.forEach { letter ->
                assertTrue("$letter -> $group", azNameMatches(letter, listOf(group)))
            }
        }
        assertTrue(azNameMatches("a\u0301", listOf(AzGroup.LATIN_AC)))
        assertTrue(azNameMatches("a\u0304", listOf(AzGroup.LATIN_AC)))
        assertFalse(AzGroup.LATIN_PS.label.contains('ß'))
        assertFalse(AzGroup.CYRILLIC_DZ.label.contains('Ё'))
    }

    @Test
    fun multilingualAliasesDoNotChangeVisibleButtonLabels() {
        assertEquals(
            listOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"),
            AZ_LATIN_GROUPS.map(AzGroup::label),
        )
        assertEquals(
            listOf("АБВГ", "ДЕЖЗ", "ИЙКЛ", "МНОП", "РСТУ", "ФХЦЧ", "ШЩЪЫ", "ЬЭЮЯ"),
            AZ_CYRILLIC_GROUPS.map(AzGroup::label),
        )
    }

    @Test
    fun kazakhNameUsesLetterGroupsInsteadOfSymbolFallback() {
        assertTrue(
            azNameMatches(
                "Қайрат",
                listOf(
                    AzGroup.CYRILLIC_IL,
                    AzGroup.CYRILLIC_AG,
                    AzGroup.CYRILLIC_IL,
                    AzGroup.CYRILLIC_RU,
                    AzGroup.CYRILLIC_AG,
                    AzGroup.CYRILLIC_RU,
                ),
            ),
        )
        assertFalse(azNameMatches("Қайрат", listOf(AzGroup.SYMBOL)))
    }

    @Test
    fun extendedEmojiGraphemesConsumeOnePosition() {
        listOf("👨‍👩‍👧‍👦", "🇨🇦", "👍🏽", "🏳️‍🌈").forEach { emoji ->
            assertTrue(emoji, azNameMatches(emoji + "A", listOf(AzGroup.SYMBOL, AzGroup.LATIN_AC)))
        }
    }

    @Test
    fun actualViewportCapacityIsNotClampedToPagerRange() {
        assertEquals(0, azVisibleCapacity(100, 24, 99))
        assertEquals(1, azVisibleCapacity(100, 24, 100))
        assertEquals(2, azVisibleCapacity(100, 24, 224))
        assertEquals(1, azVisibleCapacity(100, 24, 223))
        assertEquals(8, azVisibleCapacity(100, 24, 968))
        assertEquals(0, azVisibleCapacity(0, 24, 800))
        assertEquals(8, azVisibleCapacity(100, -1, 800))
    }

    @Test
    fun numericRowsKeepOneOrderAndFinalDoubleWidthBackspace() {
        assertEquals(
            listOf(AzGroup.SYMBOL, AzGroup.DIGIT_01, AzGroup.DIGIT_23, AzGroup.DIGIT_45).map(AzKey::Group),
            azNumericRows(false)[0],
        )
        assertEquals(
            listOf(AzKey.Group(AzGroup.DIGIT_67), AzKey.Group(AzGroup.DIGIT_89), AzKey.Backspace),
            azNumericRows(false)[1],
        )
        assertEquals(AZ_NUMERIC_KEYS, azNumericRows(true).single())
        assertEquals(AZ_NUMERIC_KEYS, azNumericRows(false).flatten())
        assertEquals(2, AzKey.Backspace.columnSpan)
        AZ_NUMERIC_KEYS.filterNot { it == AzKey.Backspace }.forEach { assertEquals(1, it.columnSpan) }
        AzGroup.entries.forEach { assertEquals("ic_" + it.icon, iconDrawableName(it.icon)) }
    }

    @Test
    fun immutableSnapshotSeparatesAppliedRowsFromDraftCandidates() {
        val items = listOf(item("Zulu"), item("A"), item("Beta"), item("Ab"), item("Ё"))
        val query = AzSearchQuery(items, listOf(AzGroup.LATIN_AC), listOf(AzGroup.LATIN_AC, AzGroup.LATIN_AC))
        val snapshot = AzPreparedSection(items).snapshot(query)
        assertEquals(listOf("A", "Beta", "Ab"), snapshot.visibleItems.map { it.label })
        assertEquals(listOf("Ab"), snapshot.draftItems.map { it.label })
        assertTrue(snapshot.resultCounts.isEmpty())
        assertFalse(query.sameAs(AzSearchQuery(items.toMutableList(), query.applied, query.draft)))
        assertFalse(query.sameAs(AzSearchQuery(items, query.applied, emptyList())))
        assertTrue(query.sameAs(AzSearchQuery(items, query.applied, query.draft)))
    }

    @Test
    fun explicitApplyRetainsPreviewScrollAnchorAndFocus() {
        val items = listOf(item("Alpha"), item("Beta"), item("Bravo"), item("Charlie"))
        assertEquals(
            SelectorAnchor(page = 1, index = 2, offset = 37, focusedItemId = "Bravo"),
            retainedSelectorAnchor(items, index = 2, offset = 37, focusedItemId = "Bravo", pageSize = 2),
        )
        assertEquals(
            SelectorAnchor(page = 1, index = 3, offset = 37, focusedItemId = "Charlie"),
            retainedSelectorAnchor(items, index = 99, offset = 37, focusedItemId = "Removed", pageSize = 2),
        )
    }

    @Test
    fun preparedSnapshotMatchesReferenceEngineAcrossAllGroups() {
        val items =
            ("ABC DEF GHI JKL MNO PQRS TUV WXYZ АБВГ ДЕЖЗ ИЙКЛ МНОП РСТУ ФХЦЧ ШЩЪЫ ЬЭЮЯ 01 23 45 67 89 # 東京 ١ ß ẞ Ё 👨‍👩‍👧‍👦")
                .split(" ")
                .map(::item)
        val prepared = AzPreparedSection(items)
        (listOf(emptyList<AzGroup>()) + AzGroup.entries.map { listOf(it) }).forEach { steps ->
            val snapshot = prepared.snapshot(AzSearchQuery(items, steps, steps))
            assertEquals(filterAzItems(items, steps), snapshot.visibleItems)
            assertEquals(azGroupResultCounts(items, steps), snapshot.resultCounts)
        }
    }

    @Test
    fun cancellationCannotPublishAPartiallyPreparedSnapshot() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(CancellationException::class.java) { AzPreparedSection(listOf(item("Alpha"))) }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun modalGateRejectsUnderlyingNavigationPlaybackAndPositionCallbacks() {
        assertSearchActionGate()
    }

    @Test
    fun reloadOrdersKeepParentAndChildFiltersIndependentAndSourceOrdered() {
        val parent = listOf(item("Bravo"), item("Alpha"), item("Zulu"))
        val child = listOf(item("Zoo"), item("Apple"), item("Zebra"))
        val parentSteps = listOf(AzGroup.LATIN_AC)
        val childSteps = listOf(AzGroup.LATIN_WZ)
        val orders = AzReloadOrders(listOf(parent to parentSteps, child to childSteps, parent to parentSteps))
        assertEquals(listOf("Bravo", "Alpha"), orders.order(parent, parentSteps))
        assertEquals(listOf("Zoo", "Zebra"), orders.order(child, childSteps))
        assertThrows(IllegalArgumentException::class.java) { orders.order(parent, childSteps) }
        assertThrows(IllegalArgumentException::class.java) { orders.order(parent.toMutableList(), parentSteps) }
    }

    private fun assertSearchActionGate() {
        listOf(
            PlayerUiAction.TogglePlayback,
            PlayerUiAction.Previous,
            PlayerUiAction.Next,
        ).forEach { assertFalse(it.allowedDuringAzOverlay()) }
        listOf(
            PlayerUiAction.OpenMain,
            PlayerUiAction.NavigateParent,
            PlayerUiAction.RememberFocus("row"),
            PlayerUiAction.RememberSelectorPosition(2, 10, 5),
            PlayerUiAction.OpenRowActions("row"),
            PlayerUiAction.NavigateTo("row"),
            PlayerUiAction.Play("row"),
            PlayerUiAction.CancelAzSearch,
            PlayerUiAction.AzBackspace,
            PlayerUiAction.IdleTimeout,
            PlayerUiAction.SelectAzGroup(AzGroup.SYMBOL),
            PlayerUiAction.ReportHierarchyViewport(100, 24, 800, 6),
        ).forEach { assertTrue(it.allowedDuringAzOverlay()) }
    }

    @Test
    fun rgbSubtractIsOneStrictFileOnlyByte() {
        listOf(0, 32, 255).forEach { assertEquals(it, UiConfig.validateAzRgbSubtract(it)) }
        listOf(-1, 256, 32.0, "32", true).forEach {
            assertThrows(IllegalArgumentException::class.java) { UiConfig.validateAzRgbSubtract(it) }
        }
    }

    @Test
    fun fixedGapRowsPreserveStandardTargetsAndBoundedOverflow() {
        assertEquals(968, azRowMinimumWidthDp(8, true))
        assertEquals(376, azRowMinimumWidthDp(4, false))
        assertEquals(76, azRowMinimumWidthDp(1, false))
        assertEquals(0, azRowMinimumWidthDp(0, true))
        assertThrows(IllegalArgumentException::class.java) { azRowMinimumWidthDp(-1, true) }
    }

    @Test
    fun numericRangesUseUnicodeMinusWithoutChangingMatching() {
        val ranges = listOf(AzGroup.DIGIT_01, AzGroup.DIGIT_23, AzGroup.DIGIT_45, AzGroup.DIGIT_67, AzGroup.DIGIT_89)
        assertEquals(listOf("0−1", "2−3", "4−5", "6−7", "8−9"), ranges.map { it.label })
        ranges.forEach { group ->
            listOf(group.label.first(), group.label.last()).forEach { digit ->
                assertTrue(azNameMatches(digit.toString(), listOf(group)))
            }
            assertFalse(azNameMatches(group.label, listOf(group, group)))
        }
    }

    @Test
    fun disabledKeyOpacityIsAFileControlledStrictByte() {
        listOf(0, 51, 255).forEach { assertEquals(it, UiConfig.validateAzDisabledAlpha(it)) }
        listOf(-1, 256, 51.0, "51", true).forEach {
            assertThrows(IllegalArgumentException::class.java) { UiConfig.validateAzDisabledAlpha(it) }
        }
    }
}
