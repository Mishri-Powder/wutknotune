# Riff project configuration

> Owner/scope: cross-system global configuration source and validation

Project-added genre identities, aliases and classifier evidence assignments are delegated to
[Project genre additions](/Riff/LATEST/project-genre-additions).

Build-time album/Singles language evidence is owned by
[Catalog language evidence](/Riff/LATEST/catalog-language-evidence).

## Configuration registry

All tunable UI values and intentional fixed constants belong to one versioned registry; no UI magic
numbers. The APK default file is complete, safe and build-validated. The optional user file lives at
`player/ui_config.json` under Player's active app-private root. Absence is normal; never search
shared/provider media storage. `schemaVersion` is exactly `3`; reject unsupported formats without
migration. Validate defaults, overlay structure/types, then complete effective semantics. Reject the
entire user file for unreadable/empty/malformed input, unknown keys, null, invalid
types/enums/variants, non-finite values or unsafe constraints. Startup uses packaged defaults after
rejection, with one bounded visible status and stable diagnostic log. A missing file produces
neither error nor notification. The user file is read once at process startup, after admission of
the selected application-data root. No hot reconfiguration, file watcher or runtime rollback
mechanism exists: edits apply after process restart. Activity recreation, foreground return, theme
changes and window resize never reread the file.

The root contains schemaVersion, applicationMode (configurable distribution only), indexer, player,
ai, activeSkin and skins; behavior keys belong to the skin configuration. Drawable XML is not
configuration. Owner objects admit only registered fields. Configurable mode defaults phone, accepts
exactly aaos/automotive/phone and cannot vary/inherit; locked-AAOS omits it and rejects overrides.
`skins.default` exists and activeSkin names an effective skin, default `default`. Merge packaged
default → packaged active skin → user default → user active skin. Objects merge recursively; arrays
replace; scalar leaves replace; null never deletes. Partial branches/new skins are legal only when
registered keys and inheritance yield a complete valid configuration. A partial user file may omit
schemaVersion and inherit the APK's current version; an explicit value must be 3. Every named skin,
including inactive skins, must validate before any user setting is accepted.

Only registered keys permit scalar, wide/narrow, day/night or day/night-outside-wide/narrow forms.
Complete variants contain exactly their two branches with identical leaf type. Geometry cannot vary
by theme; color cannot vary by responsive form.
[Local UI color system](/Riff/LATEST/ui-color-system) owns role, composite and halo contrast checks.
WIDE uses current root width in physical dp >= wideThreshold, otherwise NARROW, recomputed on resize
without device/density/diagonal branches. The playback owner's explicit portrait/landscape row
packing is the sole local playback aspect exception; it does not change WIDE/NARROW resolution.
Fixed tokens cannot vary. Layout derives remaining space by subtraction and content count by fit; it
does not scale tokens by percentages. Larger windows add content/space. Preserve strict minima and
stable hit/visual/semantic slots; unsafe fit uses bounded paging/scrolling, never
overlapping/shrunken targets. The explicit calibration and AI geometry exceptions remain scoped.

Parsing/merge/validation runs off main/playback-critical threads and activates one immutable
consistent startup snapshot. Theme and WIDE/NARROW resolution select values from that snapshot; they
do not reload configuration or disturb playback/navigation. Indexer independently snapshots its
permitted configuration at successful startup for the epoch. Configuration cannot weaken process
ownership, host/motion safety, density/calibration, storage relocation or obligations outside the
explicitly registered policy parameters. Profile theme, long-press setting and content mode are
durable profile preferences rather than skin tokens; active-profile selection, data-root location,
density/calibration and grants are installation-scoped; rebuild is a one-shot command.

Within `skins.<name>`, the registered sections are geometry, typography, colors, shape and behavior.
Color paths put the semantic role before the day/night branch. Shape contains fixed panelAlpha
(integer 0..255, default 230) and responsive panelStrokeDp (non-negative dp, default 0). The
registered value definitions are delegated below.

A-Z RGB subtraction uses this same schema, merge and startup activation, with no consumer parser or
Settings control. Its single file-only token is owned by
[UI tokens](/Riff/LATEST/ui-tokens#search-and-disabled-control-configuration).

The current distribution supplies only the complete packaged default skin. Named user skins and the
four-layer resolver are supported; no skin editor, Settings selector or additional packaged design
is introduced.

[File-only configuration values](/Riff/LATEST/project-configuration-file-only-values) owns the
per-field defaults and activation rules, including status visibility for FINAL distributions.

General UI, default-artwork and SMPTE drawable XML is packaged as ordinary APK resources and is not
present in the default or user configuration schema. The optional user file cannot replace, add or
remove drawable resources; a `drawables` member is an unknown key and rejects the whole file under
the ordinary strict-validation rule.

All bare numbers use the declared dp/sp units; unit-suffixed strings are invalid. Current exact
screen rules and the common 10dp-body/15dp-chrome padding apply where a general token has a narrower
screen use.

<!-- AI-INTENT: Consumers share the immutable process startup snapshot. Adding a setting must preserve the JVM schema/merge/validation owner and selected-root admission boundary. -->

## FINAL status default

The
[file-only values owner](/Riff/LATEST/project-configuration-file-only-values#final-status-default)
controls the shared status default for all channels and its explicit file override.
