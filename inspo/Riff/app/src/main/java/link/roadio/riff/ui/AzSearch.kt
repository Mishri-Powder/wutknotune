package link.roadio.riff.ui

import java.text.Normalizer
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.regex.Pattern

/**
 * One shared progressive-name filter for every local media-browser container.
 *
 * A step selects a character class, not a literal character. This keeps the fixed
 * touch-target layout useful while preserving the source ordering of matching rows.
 */
enum class AzGroup(
    val label: String,
) {
    SYMBOL("#"),
    DIGIT_01("0−1"),
    DIGIT_23("2−3"),
    DIGIT_45("4−5"),
    DIGIT_67("6−7"),
    DIGIT_89("8−9"),
    LATIN_AC("ABC"),
    LATIN_DF("DEF"),
    LATIN_GI("GHI"),
    LATIN_JL("JKL"),
    LATIN_MO("MNO"),
    LATIN_PS("PQRS"),
    LATIN_TV("TUV"),
    LATIN_WZ("WXYZ"),
    CYRILLIC_AG("АБВГ"),
    CYRILLIC_DZ("ДЕЖЗ"),
    CYRILLIC_IL("ИЙКЛ"),
    CYRILLIC_MP("МНОП"),
    CYRILLIC_RU("РСТУ"),
    CYRILLIC_FCH("ФХЦЧ"),
    CYRILLIC_SHY("ШЩЪЫ"),
    CYRILLIC_SOFT_YA("ЬЭЮЯ"),
}

internal sealed interface AzKey {
    data class Group(
        val group: AzGroup,
    ) : AzKey

    data object Backspace : AzKey
}

internal val AzKey.columnSpan: Int
    get() = if (this == AzKey.Backspace) 2 else 1

internal val AZ_NUMERIC_KEYS: List<AzKey> =
    listOf(
        AzGroup.SYMBOL,
        AzGroup.DIGIT_01,
        AzGroup.DIGIT_23,
        AzGroup.DIGIT_45,
        AzGroup.DIGIT_67,
        AzGroup.DIGIT_89,
    ).map(AzKey::Group) + AzKey.Backspace

internal val AZ_LATIN_GROUPS =
    listOf(
        AzGroup.LATIN_AC,
        AzGroup.LATIN_DF,
        AzGroup.LATIN_GI,
        AzGroup.LATIN_JL,
        AzGroup.LATIN_MO,
        AzGroup.LATIN_PS,
        AzGroup.LATIN_TV,
        AzGroup.LATIN_WZ,
    )

internal val AZ_CYRILLIC_GROUPS =
    listOf(
        AzGroup.CYRILLIC_AG,
        AzGroup.CYRILLIC_DZ,
        AzGroup.CYRILLIC_IL,
        AzGroup.CYRILLIC_MP,
        AzGroup.CYRILLIC_RU,
        AzGroup.CYRILLIC_FCH,
        AzGroup.CYRILLIC_SHY,
        AzGroup.CYRILLIC_SOFT_YA,
    )

/**
 * AI-INTENT: Pack physical rows before filtering so hidden rows never move keys
 * between rows; the final double-width Backspace row remains even without candidates.
 * Authority: https://wiki.271.pics/Riff/LATEST/player-az-navigation
 */
internal fun azGridRows(
    wide: Boolean,
    available: Set<AzGroup> = AzGroup.entries.toSet(),
): List<List<AzKey>> =
    buildList {
        val columns = if (wide) 8 else 4
        addAll(AZ_CYRILLIC_GROUPS.map(AzKey::Group).chunked(columns))
        addAll(AZ_LATIN_GROUPS.map(AzKey::Group).chunked(columns))
        addAll(azNumericRows(wide))
    }.filter { row ->
        row.any { key ->
            when (key) {
                AzKey.Backspace -> true
                is AzKey.Group -> key.group in available
            }
        }
    }

internal fun azRowMinimumWidthDp(
    count: Int,
    wide: Boolean,
): Int {
    require(count >= 0)
    return count * (if (wide) 100 else 76) + (count - 1).coerceAtLeast(0) * 24
}

internal val AzGroup.icon: String
    get() = "az_" + name.lowercase(Locale.ROOT)

/** One logical order is packed by column span without responsive-mode reordering. */
internal fun azNumericRows(wide: Boolean): List<List<AzKey>> {
    val columns = if (wide) 8 else 4
    val rows = mutableListOf<List<AzKey>>()
    var row = mutableListOf<AzKey>()
    var usedColumns = 0
    AZ_NUMERIC_KEYS.forEach { key ->
        val span = key.columnSpan
        require(span in 1..columns)
        if (usedColumns + span > columns) {
            rows += row
            row = mutableListOf()
            usedColumns = 0
        }
        row += key
        usedColumns += span
        if (usedColumns == columns) {
            rows += row
            row = mutableListOf()
            usedColumns = 0
        }
    }
    require(row.isEmpty()) { "A-Z numeric controls must fill the final row" }
    return rows
}

internal fun azVisibleCapacity(
    rowHeightPx: Int,
    rowGapPx: Int,
    viewportHeightPx: Int,
): Int {
    if (rowHeightPx <= 0 || viewportHeightPx <= 0) return 0
    val gap = rowGapPx.coerceAtLeast(0).toLong()
    return ((viewportHeightPx.toLong() + gap) / (rowHeightPx.toLong() + gap)).toInt()
}

/** Identity comparison avoids walking a large immutable row tree on the UI thread. */
class AzSearchQuery(
    val source: List<BrowserItem>,
    val applied: List<AzGroup>,
    val draft: List<AzGroup>?,
) {
    fun sameAs(other: AzSearchQuery): Boolean = source === other.source && applied == other.applied && draft == other.draft
}

class AzSearchSnapshot(
    val query: AzSearchQuery,
    val visibleItems: List<BrowserItem>,
    val draftItems: List<BrowserItem>,
    val resultCounts: Map<AzGroup, Int>,
)

/** Worker-confined, current-container-only key preparation; never touches media/storage. */
internal class AzPreparedSection(
    val source: List<BrowserItem>,
) {
    private val keys =
        source.map { item ->
            checkAzCancellation()
            normalizedGraphemes(item.comparisonLabel).map(::classifyAzGrapheme)
        }

    fun snapshot(query: AzSearchQuery): AzSearchSnapshot {
        require(query.source === source)
        val visible = ArrayList<BrowserItem>()
        val candidates = ArrayList<BrowserItem>()
        val counts = linkedMapOf<AzGroup, Int>()
        val draft = query.draft ?: query.applied
        source.forEachIndexed { index, item ->
            checkAzCancellation()
            val key = keys[index]
            if (azKeyMatches(key, query.applied)) visible += item
            if (azKeyMatches(key, draft)) {
                candidates += item
                key.getOrNull(draft.size)?.let { next ->
                    counts[next] = counts.getOrDefault(next, 0) + 1
                }
            }
        }
        return AzSearchSnapshot(query, visible.toList(), candidates.toList(), counts.toMap())
    }
}

private fun checkAzCancellation() {
    if (Thread.currentThread().isInterrupted) throw CancellationException("AZ search replaced")
}

private fun azKeyMatches(
    key: List<AzGroup>,
    steps: List<AzGroup>,
): Boolean =
    steps.withIndex().all { (index, step) ->
        key.getOrNull(index) == step
    }

/** Prepared off-main for one immutable library activation, then discarded. */
internal class AzReloadOrders(
    requests: List<Pair<List<BrowserItem>, List<AzGroup>>>,
) {
    private val orders = IdentityHashMap<List<BrowserItem>, MutableMap<List<AzGroup>, List<String>>>()

    init {
        requests.forEach { (items, steps) ->
            checkAzCancellation()
            orders.getOrPut(items) { mutableMapOf() }.getOrPut(steps) {
                filterAzItems(items, steps).map(BrowserItem::id)
            }
        }
    }

    fun order(
        items: List<BrowserItem>,
        steps: List<AzGroup>,
    ): List<String> = requireNotNull(orders[items]?.get(steps)) { "Unprepared AZ reload scope" }
}

internal fun filterAzItems(
    items: List<BrowserItem>,
    steps: List<AzGroup>,
): List<BrowserItem> =
    if (steps.isEmpty()) {
        items
    } else {
        items.filter {
            checkAzCancellation()
            azNameMatches(it.comparisonLabel, steps)
        }
    }

internal fun availableAzGroups(
    items: List<BrowserItem>,
    steps: List<AzGroup>,
): Set<AzGroup> = azGroupResultCounts(items, steps).keys

internal fun azGroupResultCounts(
    items: List<BrowserItem>,
    steps: List<AzGroup>,
): Map<AzGroup, Int> {
    val counts = linkedMapOf<AzGroup, Int>()
    items.forEach { item ->
        val graphemes = normalizedGraphemes(item.comparisonLabel)
        if (azGraphemesMatch(graphemes, steps)) {
            graphemes.getOrNull(steps.size)?.let { grapheme ->
                val next = classifyAzGrapheme(grapheme)
                counts[next] = counts.getOrDefault(next, 0) + 1
            }
        }
    }
    return counts
}

internal fun azNameMatches(
    name: String,
    steps: List<AzGroup>,
): Boolean {
    val graphemes = normalizedGraphemes(name)
    return azGraphemesMatch(graphemes, steps)
}

private fun azGraphemesMatch(
    graphemes: List<String>,
    steps: List<AzGroup>,
): Boolean =
    steps.withIndex().all { (index, group) ->
        index < graphemes.size && classifyAzGrapheme(graphemes[index]) == group
    }

internal fun shouldAutoOpenAzSearch(
    itemCount: Int,
    rowHeightPx: Int,
    rowGapPx: Int,
    viewportHeightPx: Int,
): Boolean {
    if (itemCount <= 0 || rowHeightPx <= 0 || viewportHeightPx <= 0) return false
    val contentHeight = itemCount.toLong() * rowHeightPx + (itemCount - 1L) * rowGapPx.coerceAtLeast(0)
    return contentHeight >= viewportHeightPx.toLong() * 3L
}

internal fun shouldAutoApplyAzSearch(
    explicitSelection: Boolean,
    resultCount: Int,
    visibleCapacity: Int,
): Boolean = explicitSelection && resultCount > 0 && visibleCapacity > 0 && resultCount <= visibleCapacity

private val AZ_GRAPHEME_PATTERN = Pattern.compile("\\X")

private fun normalizedGraphemes(value: String): List<String> {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
    val matcher = AZ_GRAPHEME_PATTERN.matcher(normalized)
    return buildList {
        while (matcher.find()) add(matcher.group())
    }
}

private fun classifyAzGrapheme(grapheme: String): AzGroup {
    val firstCodePoint = azClassificationCodePoint(grapheme) ?: return AzGroup.SYMBOL
    if (!Character.isLetterOrDigit(firstCodePoint)) return AzGroup.SYMBOL
    val folded = String(Character.toChars(Character.toUpperCase(firstCodePoint)))
    return when (folded) {
        "0", "1" -> AzGroup.DIGIT_01
        "2", "3" -> AzGroup.DIGIT_23
        "4", "5" -> AzGroup.DIGIT_45
        "6", "7" -> AzGroup.DIGIT_67
        "8", "9" -> AzGroup.DIGIT_89
        "A", "B", "C", "Æ" -> AzGroup.LATIN_AC
        "D", "E", "F", "Đ", "Ð", "Ə" -> AzGroup.LATIN_DF
        "G", "H", "I", "Ħ" -> AzGroup.LATIN_GI
        "J", "K", "L", "Ł" -> AzGroup.LATIN_JL
        "M", "N", "O", "Œ", "Ø", "Ŋ" -> AzGroup.LATIN_MO
        "P", "Q", "R", "S", "ß", "ẞ" -> AzGroup.LATIN_PS
        "T", "U", "V", "Þ" -> AzGroup.LATIN_TV
        "W", "X", "Y", "Z" -> AzGroup.LATIN_WZ
        "А", "Б", "В", "Г", "Ә", "Ӑ", "Ӓ", "Ґ", "Ғ", "Ѓ", "Ҕ" -> AzGroup.CYRILLIC_AG
        "Д", "Е", "Ж", "З", "Ё", "Ђ", "Є", "Ӗ", "Җ", "Ѕ", "Ҙ", "Џ" -> AzGroup.CYRILLIC_DZ
        "И", "Й", "К", "Л", "І", "Ї", "Ӣ", "Ј", "Қ", "Ҡ", "Ќ", "Љ" -> AzGroup.CYRILLIC_IL
        "М", "Н", "О", "П", "Ң", "Ҥ", "Њ", "Ө", "Ӧ" -> AzGroup.CYRILLIC_MP
        "Р", "С", "Т", "У", "Ҫ", "Ћ", "Ў", "Ӯ", "Ү", "Ұ", "Ӳ", "Ӱ" -> AzGroup.CYRILLIC_RU
        "Ф", "Х", "Ц", "Ч", "Ҳ", "Һ", "Ҷ", "Ҹ", "Ӌ" -> AzGroup.CYRILLIC_FCH
        "Ш", "Щ", "Ъ", "Ы" -> AzGroup.CYRILLIC_SHY
        "Ь", "Э", "Ю", "Я" -> AzGroup.CYRILLIC_SOFT_YA
        else -> AzGroup.SYMBOL
    }
}

/** One source grapheme remains one search step after compatibility/mark folding. */
private fun azClassificationCodePoint(grapheme: String): Int? {
    val decomposed = Normalizer.normalize(grapheme, Normalizer.Form.NFKD)
    var offset = 0
    while (offset < decomposed.length) {
        val codePoint = decomposed.codePointAt(offset)
        val type = Character.getType(codePoint)
        if (
            type != Character.NON_SPACING_MARK.toInt() &&
            type != Character.COMBINING_SPACING_MARK.toInt() &&
            type != Character.ENCLOSING_MARK.toInt()
        ) {
            return codePoint
        }
        offset += Character.charCount(codePoint)
    }
    return null
}
