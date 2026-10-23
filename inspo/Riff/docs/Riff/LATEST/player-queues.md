# Player queues and playlists

> Owner/scope: immutable execution queues, permanent playlists, accepted commands and reactions

## Queues, playlists and history

Playback consumes only one finite, immutable, fully materialized, validated and durable `READY`
`PlaybackQueueRevision`, activated by an atomic pointer/checkpoint switch. Source containers,
playlist edits, library replacement, reactions, mode/profile changes and AI inputs never rewrite an
active revision. Failure leaves the old revision/checkpoint restorable. Media3 executes its ordered
items; its list/index/position are not storage authority. Every item/callback maps to exact stable
occurrence identity and `Media.id`. Resolve eligible alternate bindings for that same ID; bounded
unavailability handling must not rewrite the revision. Shuffle persists a permutation/checkpoint of
that revision; Repeat repeats it. `TEMPORARY` describes retention only.

### Deferred command ownership

Every accepted deferred profile-scoped command captures its originating Player store/session and
profile before enqueueing. Reactions, queue materialization, AI selection/history and user
preferences use that captured identity for durable work. A completion from an obsolete store/session
or profile must not alter current navigation, UI preferences or activate playback. An already
accepted durable write remains owned by its original profile.

If resolving a binding URI succeeds but opening that physical source fails, try the remaining
eligible bindings of the same immutable occurrence before the existing unavailable policy. Each
candidate is attempted at most once per open; queue replacement/cancellation ends that attempt. This
fallback neither rewrites the queue nor creates a user-skip event.

### Play and Add

Manual Play and Add enforce technical playability but ignore all reactions, including inherited Ban.
Direct Album/Title Play and Add preserve all declared ALBM positions, including equal MediaIDs.
[Cuetree](/Riff/LATEST/album-grouping-positions) resolves physical copies before publication.
Artist, Genre and recursive Folder sources deduplicate MediaID at the first stable occurrence.
Playlist insertion order and intentional duplicate occurrences are preserved. Separate deliberate
Add commands can append duplicates; command delivery IDs make retries idempotent. Contextual track
Play uses the complete canonical Album or recursive current-Folder sequence with the selected
occurrence current at its existing index, without rotation. Playlist track Play rotates the pinned
playlist revision at the exact selected occurrence. All tap/long-press/accessibility/visible Play
triggers share this operation. Container Play resumes its saved context when available, otherwise
materializes its complete subtree from the beginning. Bookmarks collections are the exception: their
Play action is disabled, and playback starts from a selected mark. An explicitly selected track
always starts at zero in its canonical containing context and replaces that context's saved
position. Album/Title context is assigned only by actual Album/Title browsing, never inferred from a
track played in Folders, a playlist, an Artist/Genre aggregate or an AI queue. No separate Continue
action or Continue-from-saved M/A Settings preference exists. Add retains its explicit selected-row
source semantics. Exact browse qualification/order, row actions and contextual-source presentation
belong to the hierarchy contract.

### Permanent playlists

Permanent playlists use immutable ordered revisions. Each accepted asynchronous mutation freezes its
profile; transactions and completion use that identity, never a later active User. Stale completion
cannot navigate a replacement session. Names trim to nonempty, fit `maxStringLength` grapheme
clusters, and are unique within that profile under NFKC plus locale-independent full case folding
while preserving display spelling. Add atomically appends the materialized result to a new revision;
success opens the destination's first page in Music mode. In Books mode, the source row actions
remain open because the Music destination is hidden by the Books projection. Rename commits a
revision without changing an active queue; Delete needs named confirmation and preserves an active
queue. Remove targets exactly one occurrence. A permanent Music playlist MUST contain at least one
occurrence. Creating one without tracks is rejected; removing or expiring its last track deletes the
playlist. Temporary source unavailability does not remove occurrences. An unavailable occurrence
retains its known title, is gray and cannot be played, but can be removed. Book collections and
their frozen queues/marks follow [Audiobook bookmarks](/Riff/LATEST/player-bookmarks). No nesting,
member-playlist objects, reordering or live-AI links exist. AI Add generates once and appends that
frozen ordered result without activating playback.

New/Rename/Delete use the mandatory [shared modal component](/Riff/LATEST/ui-modal-dialogs). Delete
shows the complete playlist name separately and opens no name field/IME. Blank input cannot confirm.
Before OK, Cancel/Back/outside discards the draft. Accepted OK makes the write non-cancellable,
disables both actions and blocks Back/outside/idle departure until its result. Validation/write
errors retain the draft and appear accessibly inside the common dialog.

### Reactions

Reactions are profile-scoped and keyed by exact object identity. Like/Dislike are mutually exclusive
soft preferences with selected-again Neutral; Ban is an independent flag and hard future-AI
exclusion. All six combinations remain valid for AI-eligible Music. AutomaticBook and effective Book
media expose no Ban, Dislike or Like action or semantics, and the controller rejects a direct
reaction request for them even when an automatic book is manually browsed as Music. An aggregate row
exposes reactions only when every materialized member is AI-reaction-eligible. For an AutomaticBook
or effective Book row, the three absent reaction slots reserve no layout space: the title/body
expands to the More control with exactly targetSpacingMin between the two targets, and More remains
final. Playlist containers share the compact Bookmarks body-and-More row under the
[hierarchy owner](/Riff/LATEST/hierarchy-screen-scope-and-shared-implementation#rows-and-actions);
eligible Music member tracks retain their reactions. Entering/removing any permitted reaction never
seeks, advances, pauses, reconstructs queues or mutates another reaction dimension. Player buttons
target the current `Media.id`; absent or AI-ineligible media leaves the three stable reaction slots
inert.

[Listening history](/Riff/LATEST/player-listening-history) owns attempt events and retention.
[Audio focus and restoration](/Riff/LATEST/player-audio-focus) owns interruption and resume policy.
