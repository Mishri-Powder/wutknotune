# Riff SETTINGS_SCREEN contract — 6. Save data on and DATA_STORAGE_SELECTOR

> Owner/scope: bounded section of Riff/LATEST/settings-screen

## 6. Save data on and DATA_STORAGE_SELECTOR

The More-page Save data on… row shows the current system-provided storage label followed by one
Settings gear named `Change data storage`. It contains no HU/SD radio choices. The gear opens
`DATA_STORAGE_SELECTOR`; the current label and unused row area do nothing.

The child top bar contains explicit Cancel at START, title `Save data on`, paging controls where
needed and explicit OK at END. System Back is Cancel. The list contains Internal storage followed by
every currently mounted, writable, removable, non-emulated app-specific external root in stable
volume-identity order. Each external row shows Android's system-provided description and has stable
radio semantics. Riff does not classify or infer SD versus USB.

Entry selects the active root in a child-local draft. Cancel discards it. OK on the active root
returns without work; OK on another root immediately starts the complete active-to-selected
migration defined by [Application storage](/Riff/LATEST/player-storage). Migration is
non-cancellable after commit starts and blocks a second action. Success activates the new root in
Indexer, requests exactly one full pipeline beginning with Cuetree source discovery, and returns to
the still-open parent Settings draft with the new active label. Failure returns to this child with
the original root authoritative and a bounded error. There is no post-migration confirmation dialog,
direction prompt, reverse-import path or root merge.

The child always reserves a passive, noninteractive `20sp` footer explaining: Internal is safest
while running but consumes non-replaceable flash that may be poor on inexpensive head units; SD is
replaceable and less likely to be removed accidentally but may disconnect through vibration or bad
contact; USB is easy to unplug or move; keeping state on removable media is solely the user's
responsibility and risks losing all Riff data. At cold start Riff waits for a missing selected
volume; starting over on Internal requires explicit confirmation. Waiting does not repair damaged
media or guarantee recovery. This text informs but never disables a selectable volume.

The startup waiting modal and its reset confirmation are governed by
[Application-data root and removable storage](/Riff/LATEST/player-storage). This bootstrap recovery
is not a Settings entry; it precedes grant onboarding.

## 7. Rebuild and remaining commit behavior

Rebuild DB… provides two independent checkbox-semantic scopes, `AU` and `IA`, defined in
[ReplayGain rebuild](/Riff/LATEST/replaygain-lifecycle), both initially clear. AU resets MDRG; no
separate RG choice exists. Child navigation retains the More selections. More OK submits the
selected mask once after density, Dim Backlight and ReplayGain commit; More Cancel, Back, idle or
validation failure submits none. The row never directly executes deletion or indexing before More
OK.

Storage selection and Media folders remain installation-scoped child commits. User, Theme, Long
press, Auto search and Ignore leading articles remain profile-scoped in the primary draft. Density,
Dim Backlight, ReplayGain preference and rebuild selection are installation-scoped in the
independent More draft; rebuild remains a one-shot command, not a durable preference. Draft User
changes cannot overwrite installation fields, and More OK cannot commit or discard the primary
draft.

## 8. Application modes, interaction and verification

AAOS exposes Settings only through its official permitted parked path. Automotive allows ordinary
unrestricted entry and only the registered UNKNOWN-source recovery; positive restriction has no
bypass. Phone uses normal MAIN_SCREEN entry. Child selectors inherit the legitimately opened
parent's mode gate.

Verify both exact row orders, independent primary/More drafts and child draft retention, two rebuild
scopes and AU clearing MDRG, installation-wide ReplayGain persistence and no audio effects; verify
top-bar order, edge-pinned Cancel/OK, shared scrolling/paging, WIDE/NARROW geometry, resize, font
scale, LTR/RTL, TalkBack, rotary, keyboard focus and no hidden actions. Verify Media Folders draft
add/remove, DocumentsUI success/cancel, child OK/Cancel, old-grant pipeline completion/publication
before mutation, idle acknowledgement, one new full Cuetree pipeline after either addition or
removal, mutation-failure resume, initial zero-grant entry and no URI logging. Verify every
available data volume, stable identity, system label without SD/USB inference, passive footer,
same-root no-op, active-to-selected migration, target replacement, source integrity during migration
on available storage, rollback, migration blocking and exact warning layout. Verify neither primary
nor More Cancel can undo a completed child commit; More OK commits only its installation draft and
primary OK commits only its profile draft.
