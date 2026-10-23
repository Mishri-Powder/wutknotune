# Player audiobook bookmarks

> Owner/scope: profile-owned Bookmarks collections, frozen Book Title queues and manual marks. The
> common immutable playback execution contract remains [Player queues](player-queues.md).

## Collection and identity

A Book Title is an Album browsed in Books mode. The first Mark in a Title playback context
atomically creates that profile's Bookmarks collection for the Title and freezes the complete
ordered queue of its exact occurrences and MediaIDs. This queue may mix FILM and FILF and retain
repeated IDs. Later Marks append to that same collection without seeking, pausing or rebuilding
active playback. A collection displays only `<Title>` in the Bookmarks list, has a stable Title
identity, one frozen ordered queue, and at least one mark. No empty Bookmarks collection exists. A
book's automatic saved resume context is separate: finishing the book does not delete manual marks.
A profile owns its own collections; profile or content-mode changes do not rewrite an active
playback queue.

The live Title queue persists the full retained Title membership, including unavailable items, and
the playable-to-Title ordinal mapping so this freeze remains exact after a mode change or process
restart. A mark stores the frozen queue occurrence, local microsecond position relative to that
logical playable item, and stable section title. Media3 millisecond observations are stored in
microseconds at millisecond precision. It never stores a physical source offset as the user
position. The Bookmarks list shows marks only, not every queue member, ordered by frozen queue
ordinal then local timestamp. Each row shows the section title and `(HH:)?MM:SS`, for example
`1. Испытание, 3:21`. The same queue item may have multiple marks. Selecting an available mark
activates the frozen queue at that occurrence and position, then ordinary sequential playback
continues through all following queue items. It does not jump between marks. A collection row opens
its mark list but has a visible, disabled Play action; playback starts only by selecting an
available mark. Shuffle is disabled for Books.

## Mark action and spacing

The playback Like slot becomes an equally sized Bookmark control when the active queue was started
from a Book Title or from a specific mark in that Title's frozen Bookmarks collection. A Mark made
during Bookmarks playback appends to the same collection at the exact frozen occurrence and never
rebuilds the active queue. Merely playing an effective Book file through Folders, Music, AI or an
ordinary Music playlist does not authorize Mark. A Mark of a queue item not representable at its
exact occurrence in the existing frozen collection is rejected; new Title parts are not appended to
that collection. The command captures the actual service occurrence and position, not a periodic UI
checkpoint. One press writes one mark without dialog or confirmation. A repeated delivery or
concurrent tap cannot create a duplicate.

A new mark must be at least `player.bookmarkMinSpacingSeconds` from every existing mark on the
continuous time axis of the frozen book, regardless of queue-item or chapter boundary. The
[file-only configuration](project-configuration.md#configuration-registry) defaults to 15 seconds;
equality is allowed. Existing marks remain unchanged when this startup value changes. Compare all
marks, including temporarily unavailable ones. Use known durations of intervening logical items;
update durable observations when playback provides them. Unknown required durations or unprovable
distance reject creation.

The visible Bookmark control retains its slot, size, icon and halo, uses the shared disabled role
and disabled semantics, and exposes no activation while the current position is too close, distance
is unprovable, or its exact occurrence cannot be represented. Recompute from service position on
playback updates, seeks and queue changes, and from committed marks/durations after changes or
restart. A Title without marks permits its first mark under the ordinary context and motion gates.
The active queue's source profile owns the marks, independently of the currently browsed profile.

One shared distance/admission mechanism serves the read-only UI snapshot and the atomic store guard.
Playback/UI position updates use immutable cached marks, frozen membership and durations; they
perform no SQLite queries or SAF traversal. Snapshot refreshes follow successful relevant
transactions. The command captures actual service position and rechecks inside the durable
transaction, so stale enabled state or concurrent delivery cannot bypass spacing. Disabled pointer,
keyboard and accessibility actions cannot create a mark.

Strict aaos permits Mark only while motion is UNRESTRICTED; RESTRICTED and UNKNOWN disable the
control and command. Automotive and phone permit Mark in motion without extra confirmation. This is
the sole exception to ordinary playlist-mutation policy; deleting or renaming still follows the
existing policy. The official host surface rules remain in force.

## Visibility, expiry and deletion

Books MAIN calls the launcher Bookmarks and enables it iff this profile has at least one stored
mark, even when all its media is temporarily unavailable. Music MAIN calls it Playlists and enables
it iff a nonempty Music playlist exists. Bookmarks rows and their retained section titles remain
visible but gray and non-playable while unavailable; Remove and named collection Delete remain
possible under ordinary policy. Removing the final mark deletes the collection. Named Delete
requires confirmation. Deleting a collection never changes its already active immutable playback
queue.

Temporary cached unavailability never deletes a collection or changes the frozen queue. After a
MediaID in the frozen queue disappears from the complete accepted Cuetree snapshot at TTL expiry,
delete the entire Bookmarks collection, even if no mark targets that item. If another source with
the same MediaID remains, retain it. For a Music playlist, remove expired occurrences of that ID and
delete the playlist only if it becomes empty. An available-only catalog projection, failed scan,
missing grant or temporary disconnection is not evidence of expiry. The retained Cuetree attributes
supply labels until actual expiry; never show an `Unavailable track` placeholder.

<!-- AI-INTENT: A mark addresses an occurrence in a frozen queue. Spacing uses cumulative audible duration across chapter boundaries; MediaID alone cannot identify a repeated queue position. -->
