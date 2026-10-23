# Storage roots and relocation

> Owner/scope: Player/Indexer storage boundary, retained roots and relocation

## 1. Source and process boundary

Player owns settings, profiles, queues, history and playback checkpoints. `:indexer` owns discovery,
Cuetree source/catalog cohorts and MDRG construction. Neither process opens the other's mutable
database. Every runtime store lives below its owner subtree in the one active versioned root. GNRE
is the validated uncompressed APK asset and does not migrate.

## 2. Active and retained roots

The active root is Internal or one Android app-specific external directory selected by stable volume
identity. External eligibility requires public-API evidence of mounted, writable, removable and
non-emulated storage plus a create/write/force/read/delete probe. Enumerate all eligible roots in
stable identity order. Android's system description is display text only; it never establishes
SD/USB type or identity.

Only the active root is writable authority. Inactive source directories, if retained, are not
backups, recovery authority or a preservation guarantee. Do not update or merge roots in parallel. A
destination replaces only its exact Riff root after validation; unrelated files and parents remain
untouched.

The [Player storage owner](/Riff/LATEST/player-storage#application-data-root-and-removable-storage)
defines the shared selected-root waiting gate, retry interval, exact recovery dialogs, confirmed
fresh Internal reset and accepted removable-media risks. Activity, playback and Indexer entry points
all obey that gate before opening root-bound data. A matching display name is never evidence that
the selected volume has returned. Bootstrap data does not authorize a fallback database.

## 3. Complete migration

The Player coordinator rejects writes, quiesces playback, requests an acknowledged Indexer
cancellation fence, closes owner SQLite/WAL/SHM/journal state and releases all descriptors/mappings
before copying. It freezes a complete relative inventory and uses bounded buffers. The destination
transaction is on the selected filesystem and cannot reuse a published filename.

Validate target identity, capacity and writability, then verify the complete relative inventory by
paths, types, lengths and XXH3-64 checksums, including SQLite companions and immutable data/sidecar
pairs. This verifies byte-faithful transfer; ordinary opening owns semantic validity. Force files
and the transaction marker. If an old target Riff root exists, quarantine it within the same parent,
atomically publish the validated transaction, switch bootstrap authority, then remove only the
quarantined target. While the source medium is available, do not delete the source during the
transaction. Interrupted-migration recovery either restores the former target and source authority
or completes the new target authority. Failure reports a bounded error and leaves the source
selected. After the new root becomes authoritative, Player activates that root in Indexer and
requests exactly one full pipeline beginning with Cuetree source discovery. The copied highest valid
generations remain readable until replaced through ordinary publication; migration requests no
database rebuild.

<!-- AI-INTENT: Selected-root absence gates all root-bound consumers. A secondary directory containing data is not permission to create or activate a replacement session. -->

## Display-brightness recovery record

The [Player storage owner](/Riff/LATEST/player-storage#display-brightness-recovery-record) defines
the minimal device-protected brightness recovery record. It is installation bootstrap state and is
not copied with the selected application-data root.
