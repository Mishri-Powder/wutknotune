# Player search and ordering

> Owner/scope: Player search/sort normalization and leading articles

## Leading articles in search and ordering

`Ignore leading articles` is a per-profile Boolean Settings preference, default true for all three
profiles, committed only by Settings Save. No uses complete names. Yes removes exactly one initial
standalone article and following whitespace from the comparison/search name, case-insensitively,
using the union of all supported languages regardless of the media tag's presumed language. Leading
whitespace is skipped only while finding a removable article; an article-only name or a token joined
to punctuation remains unchanged. Display text, tags, all identity/grouping keys and source locators
remain exact. Its control follows the
[shared Boolean presentation](/Riff/LATEST/ui-foundation#boolean-preference-controls).

The project configuration contains the complete supported-language article map: en `a, an, the`; es
`el, la, lo, los, las, un, una, unos, unas`; de
`der, die, das, den, dem, des, ein, eine, einer, eines, einem, einen`; pt
`o, a, os, as, um, uma, uns, umas`; ru has an empty list. No tag-language detection is attempted.
`Die Hard` matching/sorting under H is an explicitly accepted ambiguity. Whole-word,
case-insensitive matching never strips prefixes from words such as `Theatre` or `Loser`.

One shared name operation feeds alphabetical label ordering and every A-Z
key/count/preview/navigation-repair path. Alphabetical Albums/Titles, Artists/Authors, Genres,
folder names and playlist names use the resulting key with the existing NFKC/ROOT-lowercase
ordering; original full label and stable identity break ties. Folders still precede files.
Album/Title Disc/Track/filename order, folder file order, playlist occurrences and active queue
order remain unchanged; A-Z may search their track titles through the same article operation while
preserving order. Search continues to use the existing grapheme and group table after article
removal.

Saving the setting or switching profiles invalidates prepared search keys and ordering together.
Obsolete asynchronous work must not publish; stable IDs, navigation context and playback state are
retained. Sorting and key preparation run off main/playback-critical threads. This changes neither
stored playlist-name uniqueness nor media/attribute identity.
[Player state](/Riff/LATEST/player-state) owns the current SQLite schema and stores the Boolean.
Older schemas are unsupported under its pre-release reset policy, with no migration, dual reader or
default fallback for a missing column. Desktop exports require the same current schema.

## Technical catalog display names

The [Singles display projection](/Riff/LATEST/player-catalog-membership#technical-singles-display)
retains the undecorated catalog title as the comparison/search input and full-name tie breaker.
Visible Unicode decoration never changes alphabetical order, A-Z matching/counts or article policy.
Other rows continue to use their exact visible name for comparison and full-name ties.
