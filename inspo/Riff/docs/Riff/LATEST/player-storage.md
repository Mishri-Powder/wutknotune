# Player application-data storage

> Owner/scope: Player-owned application-data selection and relocation

## Application-data root and removable storage

`ApplicationDataSelection` is either fixed `INTERNAL` storage, the default, or one currently
available Android app-specific directory on a mounted, writable, removable, non-emulated volume
identified by its stable Android volume identity. Enumerate every eligible external volume
deterministically and display Android's system-provided description. Riff MUST NOT classify, infer
or guess whether a volume is SD or USB and MUST NOT use a hidden API, caption heuristic or mutable
mount path for that purpose. The selection screen explains the general Internal, SD and USB
tradeoffs; the user is solely responsible for removable state, including possible total data loss.
Package placement does not select or validate the data root.

### One active authority

All authoritative runtime files occupy one versioned active root with separate Player and Indexer
owner subtrees: mutable stores with complete SQLite state, settings, revisions,
generations/sidecars, recovery/publication data and future unknown files. APK assets/code and
reconstructible cache are exceptions. Device-protected bootstrap stores only schema, preferred
root/volume identity, migration source/target/phase and minimum startup recovery data. GNRE remains
an immutable APK asset. Exactly one root is writable authority; roots retained after successful
migration are inactive snapshots and are never merged or updated in parallel.

### Explicit relocation

The `Save data on` child selector owns storage commit independently of the parent Settings draft.
Cancel or Back changes nothing. Its explicit OK immediately migrates the current active root to the
selected destination; selecting the active root is a no-op. No direction prompt, reverse-import mode
or UI_CONFIG switch exists. The target is never adopted or merged: replace only its exact app-owned
Riff root through an unpublished transaction while leaving unrelated storage content untouched.

One coordinator rejects writes, quiesces both processes, closes all SQLite connections/companions
and releases mappings/descriptors before freezing a complete relative inventory. Indexer
acknowledges a bounded cancellation fence after workers can no longer open/create/publish source
paths. Playback may stop and need not resume. Check target identity, space and writability; copy
every relative file with bounded buffers; validate paths/types/lengths/XXH3 checksums for the
complete relative inventory; force the transaction; atomically replace the target root and switch
bootstrap authority. Interrupted migration restores either the prior target plus original authority
or the complete new target; mixed writes and published-name reuse are forbidden. Failure keeps the
source authoritative and reports a bounded error. Relocation checks faithful transfer; ordinary
database opening and recovery own semantic validity. Relocation does not inspect or reinterpret
SQLite or immutable cohort contents. After the new root becomes authoritative, Player activates it
in Indexer and requests exactly one full pipeline beginning with Cuetree source discovery. The
copied highest valid generations remain available while this reconciliation runs; migration does not
request a database rebuild.

### Waiting for the selected volume

When the selected external application-data root is unavailable at cold start, retain its exact
stable volume identity and enter WAITING_FOR_SELECTED_STORAGE before opening Player state, playback,
Indexer, SQLite, FlatBuffer mappings or TFLite. A minimal bootstrap-only UI renders a
non-dismissible modal titled `Waiting for USB/SD storage…` with the safely bounded saved Android
volume description and the sole recovery action `Forget and reset to Internal`. Do not create or
activate an Internal root, advance store/startup/TTL counters, alter grants or import any retained
root while waiting. Packaged UI defaults and a bootstrap cache of the last resolved mode/density are
permitted; unavailable-root UI overrides are not read.

Observe mount changes and retry the same stable volume identity every 2 seconds while the launcher
is active. There is no timeout fallback. Another volume, including one with the same description,
does not satisfy the gate. After the selected root becomes mounted, writable and usable, resume
ordinary startup exactly once with that root and its existing state. Losing the root between the
probe and opening it returns to waiting where possible; this check cannot prevent a native fault
after mapping. Service-only starts independently enforce the same admission check and expose no
usable playback session or Indexer data while blocked. The gate precedes zero-SAF-grant onboarding
and release-age acknowledgement.

### Confirmed fresh Internal reset

The recovery action opens the English confirmation `Reset to Internal?`, explaining
`Start over with fresh Internal data? Settings, playlists and listening history on the selected storage will not be loaded.`
Its choices are `Cancel` and `Reset`. Cancel changes nothing. Only confirmed reset durably records
reset intent, initializes a fresh Internal root and atomically switches preferred authority before
ordinary startup. Crash recovery may finish that explicitly authorized transaction; an old retained
Internal database must not be mistaken for fresh state. Touch no external content or unrelated
files. After reset, a returned external root is only an explicit migration destination; never
automatically reactivate or merge it. Serialize arrival, confirmation and reset so only one startup
wins; a stale confirmation cannot reset a root that already resumed.

Waiting and reset confirmation use the mandatory
[shared modal component](/Riff/LATEST/ui-modal-dialogs), including common typography and icon-only
actions with the exact accessible recovery/Reset/Cancel names. Waiting keeps recovery intent visible
in its body. This changes presentation only; no root-bound configuration is read before admission.

The waiting modal blocks underlying navigation/input and follows the application-modes recovery
policy. Local phone permits confirmation by tap. Local automotive permits the ordinary parked path;
with unavailable UNKNOWN motion evidence, deliberate long-press or its equivalent accessibility
action on this recovery control may open confirmation. This narrowly scoped recovery never opens
Settings or unlocks other controls. Positive restrictions always block reset. Actual AAOS requires a
supported parked/host path; if live evidence is absent, omit or disable reset. Observe the same
restrictions again when committing reset. All dialog text and hand-written comments/AI instructions
are English.

### Limits of recovery

Live loss of mapped backing storage remains accepted user risk, including SIGBUS/process termination
and permanent data loss. Waiting preserves the selection and avoids software-induced start-over
during a delayed mount; it is not backup, hot fallback, a HU replica, cross-root restoration or a
guarantee that damaged media can be recovered. Normal same-root integrity and explicit migration
still apply.

## Display-brightness recovery record

The optional
[system-brightness workaround](/Riff/LATEST/player-inactivity#optional-system-brightness-workaround)
may keep exactly its original/applied brightness pair under device-protected `riff-bootstrap` as
minimum installation-scoped startup recovery data. This record describes a global Android setting,
not selected-root Player content; it is not copied during relocation. It is read only after selected
root admission. The optional user configuration remains solely in the selected Player root.
