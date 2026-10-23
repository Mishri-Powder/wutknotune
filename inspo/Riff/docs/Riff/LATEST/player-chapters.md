# Player chapters and checkpoints

> Owner/scope: Player chapter transport and listening checkpoints

## Chapter transport and checkpoints

Player reads only indexed TOC of the actual playback binding. More than one distinct valid
chronological start is required for navigation, marker checkpoints and the chapter line; exclude
starts outside playable duration when that duration is known. Zero/singleton behaves as no TOC; a
valid Nero singleton stays stored/exported. Do not repair malformed JSON or add stored zero;
implicit file start is not counted as a chapter.

FWD seeks the next strictly later stored start, otherwise ordinary next occurrence with current
order/Repeat/Shuffle/end behavior. REW uses the current track's effective category to select
`player.rewindPreviousThresholdSeconds.audiobook` or `.music`, then applies it relative to the
current chapter start: beyond it restart that chapter; at/below it seek the preceding boundary. File
zero is a boundary when the first stored start is later. Without a preceding boundary use ordinary
Previous/first-occurrence fallback; entry into that previous occurrence seeks its last usable TOC
marker, or zero when it has no usable multi-chapter TOC. One dispatcher handles
local/accessibility/supported media-session commands in both modes, including in-file availability
for single-entry queues. Chapter seeking preserves queue revision/occurrence, playing state,
Repeat/Shuffle/reactions. Natural file completion uses ordinary occurrence transition and preserves
queue order.

PLAYBACK_SCREEN shows Track Title/Artist/Album at 60/44/32 sp. The Artist line joins the track's
indexed Artist components with exactly comma followed by one space in deterministic canonical MDIA
Attribute-ID order; the order remains identical across repeated indexing, publication and restart
for unchanged component identities. Usable multi-chapter TOC adds a fourth 24 sp line: indexed
nonblank chapter title, otherwise `Chapter %d` with one-based index. Current chapter is the last
start at/before position, clamped to first/last entry. Player does not repair title again or
substitute it for Track Title.

One durable checkpoint writer serves Music and Book. Any user manipulation requests the latest
state, limited to one manipulation-triggered save per 30 monotonic seconds since the last successful
save. Within that window, retain a pending request and save the freshest state at the deadline even
when paused. Natural or explicit track/usable-section transitions save immediately and take
precedence over the manipulation limit. While actively playing, save at 60 monotonic seconds since
the last successful save of any kind. Paused unchanged state needs no periodic save. Successful
writes reset the deadlines; failed or rejected writes do not. Multi-marker seeks save only final
actual milliseconds and create no extra history/start/reaction events.

Serialize/coalesce writes with captured profile, context, queue revision, occurrence, Media.id,
binding preference, actual position, requested play/pause and Repeat/Shuffle. Save the outgoing
context before replacing a queue or committing a profile switch. Final orderly shutdown drains the
writer; durability cannot depend on exit callbacks. Stale callbacks cannot overwrite a different
active revision or recreate a completed context.
