# Player state and profiles

> Owner/scope: Player mutable state, profiles and recovery

## Player state, profiles and recovery

Player owns one schema-versioned `player-state.sqlite` with `WAL`, `synchronous=FULL` and
`foreign_keys=ON`. The current pre-release schema is 15. Queue, playlist and listening-history
MediaIDs store the presence of Cuetree `ID.lo` separately from its numeric value; a short ID and a
full ID whose low word is zero are distinct. Unsupported pre-release Player schemas are discarded by
the existing clean-reset rule, with no schema migration or compatibility reader. That reset also
discards old Player profiles, playlists, history and checkpoints; the selected Cuetree library
cohort is independent and remains available. Exactly three built-in profiles have stable IDs and
labels `1`, `2`, `3`; profile `1` is the default. There is no profile CRUD, per-profile database or
directory, or copied library.

### Profiles and installation state

Profile state comprises content mode, the shared per-profile
[audiobook speed](/Riff/LATEST/player-audiobook-speed), Folder/Track classification, Theme (default
SYSTEM), Long press starts playback (default false), Auto search (default true), Ignore leading
articles (default true), listening history, saved playback contexts and the last-context pointer,
and named permanent Music playlists and Bookmarks collections/revisions. Bookmarks store their Title
identity, complete frozen queue, marks, and observed chapter durations. A Title playback queue also
persists the complete ordered Title membership and the mapping from playable queue ordinals to Title
ordinals; changing UI mode or restarting the process cannot turn a partly unavailable Title into a
truncated frozen Bookmarks queue. AI selection controls (queue size, PTE bands and A/V target), own
reactions and listening history are profile-owned inputs to
[automatic selection](/Riff/LATEST/player-automatic-selection). Shared Indexer audio features and
model weights remain installation inputs. Selection preferences are the explicit profile inputs
defined by the automatic-selection owner. Installation state comprises the active-profile pointer,
storage root, grants, density/calibration, Dim Backlight (default Auto), Indexer
state/configuration, rebuild commands, private user configuration and bootstrap. Fields are
installation-scoped unless explicitly assigned otherwise. The active queue/checkpoint is one
application-wide session retaining its source-profile provenance. A saved profile switch atomically
commits its settings and active ID, refreshes profile classification and preserves playback, queue,
storage, grants, density and Indexer work. Settings draft/commit details belong to the Settings
contract.

### Transaction revision

Every semantic Player transaction atomically increments a non-negative signed-64, monotonic,
clock-independent `state_revision`; clean creation starts at zero. It covers settings, reactions,
history/model, playlists, queues, active pointer and checkpoint. Wall clock, SQLite data-version and
Indexer epoch are not revision sources.

### Cold-start recovery

At cold start, before other Player database clients/writers or Player-requested Indexer launch,
validate the selected root and perform bounded recovery/snapshot maintenance within the
playback-readiness deadline. Recover the live WAL through SQLite and require full `integrity_check`.
Unsupported pre-release schemas are clean-reset conditions; permission, unavailable storage,
disk-full and application errors are not corruption. Confirmed current-schema corruption closes all
connections and quarantines the complete main/WAL/SHM/journal set. Validate snapshots newest-first
by supported schema, filename/embedded revision, structure, checksum and read-only integrity check;
atomically restore the highest valid snapshot through a forced temporary live file and directory
sync, with no stale sidecars. Reopen and validate. If no same-root snapshot is valid, report
Player-state unavailability without silently modifying damaged evidence. No HU safety replica or
cross-root recovery is provided. Unavailable selected storage follows the cold-start waiting gate;
only explicit confirmed reset authorizes fresh Internal start-over.

### Same-root recovery snapshots

Recovery snapshots are immutable `player-state-<20-digit-zero-padded-revision>.sqlite` plus
canonical eight-byte XXH3-64 `.xxh3` commit markers. Filename and embedded revision must match. They
never become a second writable authority. After successful validation, skip checkpoint/publication
when the latest valid snapshot has the same revision. Otherwise quiesce writes and run
`wal_checkpoint(TRUNCATE)` outside a transaction: require `busy=0` and complete checkpointing, with
at most one bounded retry. Failure preserves valid live state, publishes nothing and permits bounded
startup with a warning. Close all connections before raw copying; stream, force, read-only validate
and hash the temporary copy, publish data then sidecar using the immutable-publication protocol,
validate the final pair and retain at most two complete valid generations.
Incomplete/quarantine/live files do not count. Cleanup is crash-idempotent and syncs directory
removals.

Same-root SQLite recovery and snapshots are ordinary integrity mechanisms on available storage, not
backups against loss of that medium. Do not add an independent Internal/HU replica, periodic mirror,
redundant root, backup UI or other removable-loss preservation mechanism.
