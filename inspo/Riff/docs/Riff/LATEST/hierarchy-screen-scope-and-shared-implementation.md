# Riff HIERARCHY_SCREEN contract — Scope and shared implementation

> Owner/scope: bounded section of Riff/LATEST/hierarchy-screen

## Scope and shared implementation

HIERARCHY_SCREEN browses Albums/Titles, Artists/Authors, Genres, Playlists, Folders and every
descendant list. One generic Compose renderer, controller, navigation state, projection and typed
action set serves every root, both content modes and WIDE/NARROW forms. Row kind and root are data;
no category-specific screen, controller, adapter, Fragment, Activity, XML list or programmatic View
tree is permitted.

The screen consumes one immutable snapshot containing active profile, content mode, root/path, rows,
applied A-Z steps, list anchor/offset/focus, row states, catalog readiness and application policy.
Rows and actions use stable typed IDs. Obsolete source/profile/mode results never publish.
Unavailable retained Album/Title and playlist/bookmark members keep their indexed labels and
position; their Play/body activation is disabled and gray, while permitted More, Remove/Delete and
Music reaction actions remain independently available. Never synthesize an `Unavailable track` label
or dim an entire row so that its remaining actions appear disabled. The global A-Z adjustment under
[UI tokens](/Riff/LATEST/ui-tokens#search-and-disabled-control-configuration) is a uniform
search-context effect over all ordinary selector content, not an unavailable-row treatment; its
exposed actions remain enabled and operable.

## Catalog projection and ordering

MUSIC shows effective Music in Albums, Artists and GNRE-backed Genres. AUDIOBOOK shows effective
Book in Titles, Authors and non-GNRE GenreText Genres. Albums/Titles and Artists/Authors are the
real Album and Artist records from one validated combined catalog matching the accepted MDIA. An
Album/Title root row requires at least three distinct currently playable Media.ID members in the
active projection; an Artist/Author root row independently requires at least three such members
carrying that canonical component across its actual Albums/Titles. One Media.ID counts once per
entity even when equal normalized Track Artist and Album Artist values both reference it.
Artist/Author and Genre contexts retain complete actual qualifying Albums/Titles even when a child
Album/Title is below the Album/Title-root threshold. Declared totals and inferred completeness do
not affect these thresholds. Missing Album or Artist metadata creates no guessed entity. Folders
always shows the complete accessible ordinary hierarchy and Play/Add includes all available
recursive content regardless of category. Playlists may intentionally mix categories.

Albums use canonical Disc, Track and filename order. Folders list child folders before immediate
files and preserve provider filenames. Each Artist/Author row is one parsed Track Artist or Album
Artist component. Selecting or matching that one component opens a typed Albums/Titles context
containing every complete qualifying group that references it; other components need not match.
Equal normalized Track Artist and Album Artist components collapse to one artist identity. Genre
rows use the same typed complete-group context. MUSIC's file-configured excluded Genre IDs are
removed at the common Genre projection; AUDIOBOOK uses central GenreText/GNRE anti-matching.

While the combined Albums/Artists catalog is unavailable, progressive Genre labels may appear as
disabled vocabulary rows. They allow scrolling and A-Z filtering but no descent, Play, Add or More.
A matching intermediate or final combined catalog atomically replaces them with ordinary rows.

Alphabetical container labels and A-Z comparison names follow
[Leading articles](/Riff/LATEST/player-search-ordering), controlled by the saved per-profile
preference. Display and IDs are unchanged. Fixed Disc/Track/filename order and playlist occurrence
order remain intact.

## Chrome, scrolling and navigation memory

The top bar contains Parent at START, one interactive breadcrumb in the center and A-Z at END. MAIN
remains a separate safe return according to the shared top-bar navigation. Parent goes to the
immediate parent and restores its independent state. The breadcrumb exposes the current root/path
and returns to the corresponding root. After Artist/Author or Genre selection the context title is
Albums or Titles. The final current A-Z group plate returns to its preceding filter prefix both with
the keypad open and after manual or automatic closure under the
[A-Z navigation owner](/Riff/LATEST/player-az-navigation); other breadcrumb destinations and
independent container filters remain as specified there.

The ordinary list uses smooth vertical drag/fling and viewport Up/Down controls over the same scroll
state. At rest a complete row is anchored. Accessibility page position derives from the first
visible row. Content never appears behind chrome, the A-Z panel or safe insets.

Navigation memory is process-only and keyed by profile, content mode, root and path. It retains
first visible stable row ID, pixel offset, focus and applied A-Z steps for every visited container.
MAIN Playlists/Bookmarks always enters the collection-list root, restoring only that root's own
filter, anchor and focus; a remembered playlist/mark-list descendant must not replace this entry.
Child-container memories remain available for ordinary descent and Back/Parent navigation. Back
restores the exact surviving snapshot. Source updates preserve the nearest surviving row and bounded
offset; removed paths fall back to the nearest surviving ancestor. Profile/mode switching restores
its independent tree and never changes playback.

## Rows and actions

Each ordinary row uses the shared presentation selected by its materialized action eligibility. The
body is one complete semantic node. Folder and container body tap descends. Track body tap emits the
common contextual Play request. Enabled long press follows the saved long-press preference and
shares the same typed Play operation.

All ordinary entity titles use the single UI_CONFIG
`skins.<name>.colors.textPrimaryColor.<day|night>` role. Folder/file, Album/Track and Book/Music
classification never choose another title color. Disabled, focus, selected, now-playing, suppressed
and error states remain separate non-color-only state treatments.

Rows with eligible Music reactions preserve their configured geometry: WIDE uses one row; NARROW
uses the title/body tier, 24dp gap and reaction/More tier. Playlist containers, AutomaticBook,
effective Book and Bookmarks mark rows share one body-and-More presentation with no reaction tier or
placeholders; body and More share one horizontal tier with the standard 24dp gap. Their targets
remain distinct and at least 76dp.

The shared selector chooses its uniform allocation from the complete immediate source before A-Z
filtering. A source containing only body-and-More rows uses `geometry.listRowHeight` in WIDE and
`max(geometry.listRowHeight, geometry.ratingTargetNarrow)` in NARROW, preserving custom target
sizes. A source requiring Music action slots uses `geometry.listRowHeightTwoTier` in NARROW and
`geometry.listRowHeight` in WIDE. Filtering, unavailable state and profile reactions do not change
this allocation. Rendering, viewport capacity, paging and A-Z measurements use that same row height;
no category-specific list or orientation exception exists.

Ban, Dislike and Like are shown only when the row's materialized target is AI-reaction-eligible
Music. AutomaticBook or effective Book rows expose no such action or semantics, including automatic
books manually browsed as Music. Aggregate rows expose reactions only when every materialized member
is eligible. The three absent Book slots reserve no space; title/body expands to More with the
standard gap. Music playlists and Bookmarks collections use that same shared renderer and allocation
rule, regardless of member classification. Eligible Music track occurrences inside a playlist retain
their reactions. Direct controller requests for ineligible targets are rejected.

For eligible targets, Ban is independent; Like and Dislike are mutually exclusive and selected-again
returns Neutral. Reaction taps mutate only the active profile's state and never open, play, seek,
reorder or rebuild the active queue. More is always the final action slot when ROW_ACTIONS exists.

Contextual Play follows the central queue contract: Album retains complete canonical order and
selected track index; Folder recursively flattens the exact current folder; Playlist rotates its
pinned revision at the exact occurrence and preserves duplicates; Artist/Genre materialize complete
qualifying groups. Play/Add enforce technical availability and ignore reactions. Container Play,
whether from the menu, enabled long press or accessibility, resumes its saved context under the
central saved-context and TTL contract; absent data starts at the beginning. Bookmarks collection
rows are the exception: their Play stays disabled, and only a mark row starts the frozen queue.
Explicit track Play starts that track at zero in its containing context, replacing that context's
position. Album/Title bookmarks are never changed by playing their tracks through another context.
There is no additional Continue action.
