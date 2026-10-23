# Riff runtime architecture

> Owner/scope: cross-process ownership, IPC and shared runtime constraints

## Internal hash policy

Every first-party hash computed in the shipped product for identity, persisted data integrity,
source-capability revisions, model runtime validation or application-data relocation uses unseeded
XXH3-64. Each owning format specifies framing and representation: binary sidecars use canonical
eight-byte form, while textual digests use 16 lowercase hexadecimal digits. External supply-chain
and distribution requirements, such as APK signing fingerprints and provenance of imported sources,
may retain their required SHA-256 evidence outside product runtime. They do not select an internal
hash algorithm.

## Packaged Android permissions

The signed offline APK requests exactly `android.permission.FOREGROUND_SERVICE`,
`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `android.permission.WAKE_LOCK`, and
`android.permission.WRITE_SETTINGS`. The first three support the Media3 mediaPlayback foreground
service and its LOCAL CPU wake mode. MediaSession notifications use Android's notification
exemption; ordinary notifications and notification-permission requests are outside current
functionality.

WRITE_SETTINGS supports only the opt-in
[system-brightness workaround](/Riff/LATEST/player-inactivity#optional-system-brightness-workaround).
Manifest declaration does not grant access: Android's separate special access is required. Packaged
defaults never use it. No hidden display permission, root or MCU capability is requested.

The application manifest explicitly removes transitive POST_NOTIFICATIONS, ACCESS_NETWORK_STATE,
FOREGROUND_SERVICE_DATA_SYNC, RECEIVE_BOOT_COMPLETED, and the AndroidX application-specific
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION declaration/request. The pinned current runtime uses no
ContextCompat NOT_EXPORTED registration path requiring that signature permission on API 31/32.
Dependency or receiver changes must review that boundary before adoption; never weaken receiver
export behavior to preserve a smaller permission list.

The offline Interpreter uses the APK model. WorkManager automatic initialization/components and Play
Asset Delivery components are omitted from the merged application manifest; Riff schedules no
WorkManager job, dataSync service, network download or boot restart. Other AndroidX initializers
retain their independent function. Release verification checks the fully merged permission set,
absence of these unused components and the mediaPlayback foreground-service declaration. A
dependency cannot add a new permission without a reviewed contract change.

## Platform and process ownership

Riff is a Kotlin, audio-only, offline Android application (`minSdk 31`) for internal/SD/USB media
through supported Android access. One APK targets direct-install AOSP head units, phones/tablets and
projected Android Auto; application and host policies follow the registered mode contract. The
primary device has 4 GB RAM and a weak CPU; QCM6125 is the release-reference architecture. Layout
adapts to window size and density without orientation or device-model screen forks. Playback
continuity across lifecycle and ignition/power cycles is required; voice is optional.

Riff is pre-release. The source-owned edition/channel and signed-distribution policy follow
[Distribution and licensing](/Riff/LATEST/distribution). Non-final builds make no compatibility,
migration, upgrade, or persistent-data preservation promise: its application-data and immutable
formats may be deleted, reset, rebuilt, or replaced without an importer, dual reader, compatibility
shim, or upgrade path. Functional, safety, ownership, durability, and current-format
application-data relocation obligations remain required. A stable public-release compatibility
policy requires separate adoption.

Exactly two permanent processes have exclusive ownership:

- Player, the default process: UI, playback, focus, finite queues, mutable profile/listening state
  and restoration.
- `:indexer`: discovery, metadata and identity extraction, SAF source access, Cuetree identity
  injection and scan scheduling, catalog production and immutable publication. Its Android adapters
  live in `:indexer`; `:app` packages them but cannot instantiate their workflows. The independent
  Cuetree core retains its own JVM build and source export.

Process-aware initialization, separate mutable databases and explicit same-package non-exported
Binder/AIDL maintain this boundary. IPC types use owner/direction-specific
`link.roadio.riff.ipc.player` or `.indexer` namespaces. Each Indexer component declares
`android:process=":indexer"`. Neither process opens the other's mutable files. Immutable artifacts
cross only as validated exact-inode read-only descriptors; Player never reconstructs their shared
paths. A shared relocation coordinator quiesces both owners without sharing databases.

The pure JVM :configuration module owns the shared schema, strict parser, merge and validation for
both processes. APK defaults are validated during the build and compiled into immutable data. Each
process reads the optional user configuration once after selected-root admission, using the common
Android startup loader. No consumer reparses a reduced format. UI width/theme variants resolve from
the same cached snapshot, and file edits require process restart; see
[configuration](/Riff/LATEST/project-configuration).

All library-wide work is bounded, cancellable/resumable where applicable, off main/playback-critical
threads and subordinate to audio continuity. Indexer logs typed, bounded, non-secret
phase/discovery/binding/TTL/publication events, not raw locators, complete URIs or unbounded
listings. [Reliable IPC](/Riff/LATEST/reliable-player-indexer-ipc) owns SEQ/ACK delivery, duplicate
handling and sessions for control/status messages.
[Source recovery](/Riff/LATEST/indexer-source-failures) owns the Player RAM attempt journal; Player
does not interpret media. Indexer supplies bounded display-ready pipeline, rebuild and failure text
through Binder. Player may supply the lower-priority `Loading updated music library…` state after
publication and the stable empty-library state after a validated load. MAIN_SCREEN renders one
selected line; empty text/disconnect/death clears transient Indexer text.

[Automatic library recreation](/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation)
replaces incompatible or irrecoverably invalid derived library data without a needs-rebuilding
status or a manual action, while selected-storage admission and relocation fences still apply.

The Cuetree scan reports bounded completed-folder/media counts and measured
[publication-period rates](/Riff/LATEST/indexer-staging#progress-and-period-rates), clears status on
success, and reports `Music library update stopped after an error` on failure. Rebuild acceptance
uses `Preparing the selected library rebuild…`; index deletion uses
`Clearing selected library data…`. Status must remain bounded and omit raw locators and document
IDs. The Player keeps its last validated cohort through scan failure.

Prefer public Android/AndroidX facilities. Third-party dependencies need a recorded platform gap,
pinned version, owner and focused tests. Media3/ExoPlayer supplies playback. Cuetree bindings and
runtime use its independently pinned FlatBuffers Java toolchain. The two Java/Kotlin FlatBuffers
packages must remain isolated and the assembled APK must pass duplicate-class validation. Pin XXH3
to xxHash `0.8.4`. TensorFlow Lite is the production Level 2 format; pin/review the interpreter and
prohibit ONNX Runtime. [Cuegrain](/Cuegrain) owns supported container/tag parsing, gain decoding and
text repair under its registered integration contract. Riff uses its API rather than parallel
parsers. Android supplies decoder admission and the limited technical-duration/unsupported-format
fallback defined by the Indexer metadata contract.
