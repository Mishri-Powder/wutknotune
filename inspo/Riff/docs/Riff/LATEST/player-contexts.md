# Player saved contexts

> Owner/scope: Player saved playback contexts and retention

## Saved playback contexts and TTL

Each profile retains one last-context pointer and independently saved Album/Title contexts keyed by
exact Album identity. A record references a fully materialized immutable queue, including its
canonical order and exact shuffle permutation, and stores occurrence/position, binding preference,
Repeat, Shuffle and last successful save cycle. Restoration uses those saved orders; setting the
player and button flags MUST NOT generate another permutation. Selecting an explicit track creates a
fresh canonical context at that track's zero position. Ordinary Play resumes both Music and
Audiobooks; absent or expired data starts from the beginning.

Non-Album contexts (Folder, aggregate, playlist execution and AI) are temporary resume records valid
only in their current Player store cycle. A cold Player-store activation begins the next cycle and
discards temporary resume records and their last pointers. Album/Title records survive cycles
subject to TTL. The application still executes only one active session; changing profile does not
automatically replace its playing queue. Subsequent saves belong to the captured active user,
without overwriting another user's saved position. History and lifetime track statistics retain
their existing meaning.

Full non-repeating Album/Title completion deletes the corresponding user's saved record and last
pointer immediately, cancels pending saves, and leaves no automatic resume bookmark. User-created
marks in a Bookmarks collection are independent of saved-context completion and remain until
explicitly deleted or their frozen queue expires under the bookmark contract. Repeat wrap is not
completion. Technical source failure is not completion: retain the saved position, resolve the same
Media.id through eligible alternate bindings, or report unavailable without silently losing the
place.

All existing TTL defaults are 128 cycles; records specifically classified as Audiobook use three
times the base, default 384. TTL is measured from the last successful save of that record, not
reading, browsing or a save to a different record. Album/Title resume records use Player store
cycles and expire when cycle age exceeds their configured TTL; temporary records retain the shorter
next-cycle lifetime. Listening events retain their existing history-generation clock and window
boundary. Cuetree source retention follows [publication-generation TTL](../../Cuetree/catalogs.md).
Player lifetime statistics and immutable artifact retention have their own owners. A saved context
uses the existing player.recentHistoryGenerations base; no additional Settings surface exists.
