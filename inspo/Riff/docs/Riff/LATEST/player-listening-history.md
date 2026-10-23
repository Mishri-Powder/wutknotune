# Player listening history

> Owner/scope: actual playback attempts, event ownership, lifetime counts and history retention.

## Attempts and profile ownership

Every actual playback attempt has a stable `play_instance_id`. Its first real start creates exactly
one profile/media `PLAY_STARTED` and increments a non-negative signed-64 lifetime
`total_started_count` in the same durable transaction/revision. Pause/resume, preparation, focus
regain, duplicate delivery and UI recreation are not new starts; Repeat/new activation is. Overflow
is rejected. Observations never mutate explicit reactions. Record `PLAY_STARTED`, `USER_SKIPPED`,
`COMPLETED`, `CENSORED_END` with profile, Media ID, attempt ID, raw millisecond position/duration,
creation revision and `history_generation`; derive ratios instead of storing floating-point
authority. Censored/technical termination is neutral. Freeze an attempt's profile at its first
actual start; all later events for that attempt retain that profile even if the active User changes.
Seamless occurrence transitions and Repeat create a new attempt when actually playing. Completion
uses the old occurrence's actual terminal position, and explicit cross-occurrence user transport
records USER_SKIPPED; an in-file seek or technical skipped period is not a user skip.

## History generation and retention

Player's monotonic non-negative signed-64 `history_generation` advances exactly once on the first
listening event of each live store session; UI launch, maintenance and Indexer alone do not advance
it. Transactionally retain the current and preceding `recentHistoryGenerations-1` generations,
across all profiles. This file-only installation integer defaults to `128`, range `1..10000`;
audiobook event records retain three times that window, default `384`. The event captures its
effective Book/Music classification when saved. Event identity, start counting and generation
advancement are unchanged. Expiry never alters lifetime counters, reactions, learned state,
queues/playlists or checkpoints. Library disappearance, AU/IA rebuild and profile switching never
reset lifetime counts. Checkpoint cadence is defined with content classification and shared TOC
transport.

Queue revisions and command ownership remain in [Player queues](/Riff/LATEST/player-queues).
