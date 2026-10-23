# SAF empty-listing platform workaround

> Owner/scope: Riff Android SAF source admission and its local Player warning.

## Decision and evidence

Adopted and revised on 2026-10-06 after the user-authorized phone experiment. Android 16
ExternalStorageProvider can return a successful empty root child cursor although existing files
remain readable through the persisted tree grant. Riff's original marker discovery mistook that
false absence for an empty root and created renamed marker directories with different UUIDs.
[The investigation](/Riff/LATEST/saf-provider-research-20261005) owns the logs, installed-provider
inspection and phone/HU comparison.

The originally adopted plan guessed two descendant document IDs. That would deliberately violate the
SAF opaque-ID abstraction. It was withdrawn before delivery: a temporary-file experiment found a
diagnostic using supported provider APIs. Production MUST NOT fabricate descendant IDs, derive file
paths from document IDs, or retain that abandoned fallback, including for read-only grants. The
Android
[document-ID contract](https://developer.android.com/reference/android/provider/DocumentsContract.Document#COLUMN_DOCUMENT_ID)
applies without this exception.

## Admission before marker writes

For each readable persisted tree, inspect the existing Riff UUID and MediaIndexer fallback through
ordinary provider enumeration. Actual identity priority and validation remain owned by
[Cuetree integration](/Riff/LATEST/cuetree-integration): validated Riff UUID, successful normal Riff
publication when writable, then validated fallback/zero identity. Before creating or recreating any
Riff marker, inspect the raw root child response, before media or dot-name filtering:

1. Any row, including any hidden file or directory, means this false-empty failure is inactive for
   that response. Continue normal identity/publication/scanning without this diagnostic or a reboot
   recommendation.
2. With neither marker discoverable and a successful zero-row response, a writable grant permits the
   temporary-file diagnostic below.
3. A zero-row read-only grant is inconclusive. Skip that root for this pass, report its temporary
   unavailability, preserve the grant/facts and do not fabricate URIs or claim the platform bug is
   confirmed. A future writable regrant is solely a normal explicit Media Folders operation.
4. A null cursor, query exception, explicit error or loading response is unavailable enumeration,
   not a successful empty directory. No marker writes are allowed from that admission attempt.

Valid identity still requires canonical UUIDv4 and, for Riff, a valid eight-byte XXH3 sidecar.
Diagnostic opening does not validate, replace or adopt any source identity.

## Why the temporary file is necessary

A zero-row cursor alone cannot distinguish a truly empty folder from the observed platform failure.
Creating one new, empty, uniquely named non-media diagnostic file gives us a document known to
exist, with a locator issued by the provider. This is an existence witness, not a source marker,
create-if-absent test, or attempt to repair/restart the provider.

Create it with DocumentsContract.createDocument under the exact granted root. Use a random UUID in
the diagnostic name to avoid ordinary collisions. The production name has no leading dot, so a
provider's legitimate hidden-file policy cannot conceal our witness. Its .tmp suffix and binary MIME
keep it out of media admission. The provider may choose the final name; its returned URI is the
authority, so no exact-name comparison or renamed-file cleanup is needed. Open only that URI in read
mode and immediately close the descriptor; zero bytes and no checksum suffice. Do not write a
payload, inspect media or guess any descendant ID. Then repeat the same unfiltered child query:

- If the query is still successful and empty while that returned document opens, the active
  false-empty listing failure is confirmed. The provider has concealed a file known to exist.
- If the new raw listing has any row, treat this failure as inactive and continue normally after
  cleanup. The ordinary newly empty writable root can then create its Riff UUID.
- Creation, opening or enumeration failure makes the diagnostic inconclusive; skip that root without
  claiming a confirmed platform bug or publishing a successfully empty source.

In all paths after creation, delete only the exact URI returned for this diagnostic file, in a
finally cleanup. Never delete a discovered/pre-existing file, a source marker, its parent or a
renamed marker directory. Cleanup failure is typed/logged and makes the root unavailable for this
pass; it cannot authorize marker publication. A process crash can leave that unique empty non-media
file behind; no unsafe filename-pattern cleanup is permitted.

The phone experiment tested both two-byte and zero-byte files. In healthy Music the listing was 4 ->
5 -> 4 through creation/deletion. With the known failure active it remained 0 -> 0 -> 0 while
creation, direct opening and deletion all succeeded. Three repeated post-create listings agreed,
without loading/error extras. Both grants survived and every diagnostic file was deleted.
Creation/opening/deletion alone do not diagnose the bug: the contradictory listing is essential.

## Scan retention and warning

An active or inconclusive root is inactive for the current pass before marker publication. Keep its
persisted flags and previous identity/facts under existing cached-unavailability and
publication-generation TTL. Continue other admitted grants. Retry admission on the next scan; there
is no persistent ban, grant revocation, identity reset or automatic reboot.

Zero admitted roots are normal no-work input under [SAF coordination](/Riff/LATEST/indexer-staging).
With no prior cohort the ordinary empty cohort is allowed; existing records follow normal
availability/TTL. Do not confuse inactive grants with zero persisted grants, trigger onboarding or
exit the application. The main menu remains available even when empty.

Indexer sends one separate bounded Binder notice for confirmed-bug and inconclusive folders,
independent of transient status/publication. Late Player subscribers receive it. Local Player
renders one aggregate non-dismissible modal with sole OK action. Confirmed failures use title
Android storage listing error and explain that Riff skips those folders, continues others, and a
device restart may restore access. Inconclusive roots use a bounded unavailable-folder explanation
without claiming a platform bug or recommending reboot. Never show raw URIs/IDs or implementation
details in that user-facing copy.

Acknowledgement is process-memory state for the reported folder set, survives Activity recreation,
and changes no grants, playback or navigation. Status clears, Back/outside input and idle cannot
acknowledge it. Local automotive and phone share the dialog. Actual AAOS/projected Android Auto
retain supported host surfaces and restrictions; this local modal is no replacement host surface or
motion/Settings bypass. [Conformance](/Riff/LATEST/aaos-conformance) owns the official-source
review.

The local warning uses the mandatory [shared modal component](/Riff/LATEST/ui-modal-dialogs);
aggregate copy, sole OK and acknowledgement semantics remain unchanged.

## Required verification

Cover hidden-only/nonempty roots without probes, identity precedence, genuinely empty marker
write/sync/read-back, opaque returned IDs, active false-empty detection without marker changes,
temporary cleanup on each failure path, no writes for empty read-only input, normal query errors and
loading, mixed/all-unavailable grants, existing availability/TTL, late Binder subscribers,
acknowledgement/recreation and modal input/idle/focus/font-scale/RTL. Separate live phone evidence
from provider fixtures and actual vehicle-host conformance.
