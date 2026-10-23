# Riff file-only configuration values

> Owner/scope: registered scalar/value defaults and activation under
> [Project configuration](/Riff/LATEST/project-configuration).

The parent owns schema, parsing, merge, variants, validation, storage admission and immutable
startup activation. Here, global paths are literal; `behavior.*` abbreviates
`skins.<name>.behavior.*`.

| File-only global key                                        | Required value/default and activation                                                                                                                                                                                                                                                                                                                     |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `indexer.pipelineDepth`                                     | Integer `1..8`, default `3`; maximum in-flight metadata or audio-analysis items, including ordered consumption and queued preparations, using one acquisition producer; fixed for each successful Indexer startup epoch; file-only with no Settings control                                                                                               |
| `indexer.retentionGenerations`                              | Integer `1..4294967295`, default `128`; one TTL for all Cuetree record kinds, measured in publications; fixed for each successful Indexer process                                                                                                                                                                                                         |
| `player.highlightFragments`                                 | Boolean, default `false`; file-only switch that tints FILF titles in Riff-owned hierarchy rows with the active skin’s `fragmentTitleColor`; no Settings control or runtime reload                                                                                                                                                                         |
| `player.bookmarkMinSpacingSeconds`                          | Integer `1..2147483647`, default `15`; snapshot on Player-store activation, common to all profiles/themes/forms; one shared Bookmark UI/atomic-write spacing policy; existing marks are retained; no Settings control or runtime reload                                                                                                                   |
| `player.recentHistoryGenerations`                           | Integer `1..10000`, default `128`; audiobook retention is three times this value; snapshot on Player-store activation, all profiles                                                                                                                                                                                                                       |
| `player.rewindPreviousThresholdSeconds.music`, `.audiobook` | Object containing exactly integer `music` and `audiobook`, each `1..60`, defaults `3` and `15`; snapshot together on Media3 creation. The current track's effective profile category selects the value for every Previous/Rewind command, independently of the visible browse mode.                                                                       |
| `player.screenDiagonalChoicesInches`                        | Exactly 16 finite positive increasing numbers, first `6.8`, last `15.1`; default `[6.8,7.0,8.0,8.8,9.0,10.1,10.25,10.4,11.0,11.6,12.3,12.4,12.95,13.3,14.1,15.1]`; only populates SCREEN_DIAGONAL                                                                                                                                                         |
| `player.excludedGenreIds`                                   | Unique canonical signed-decimal nonzero 64-bit ID strings; default `["-100000","-100001","-100002","-100003"]`; empty disables filter; invalid/duplicate rejects whole file                                                                                                                                                                               |
| `behavior.enableStatusLines`                                | Boolean, packaged default `true` in every channel, including FINAL; controls MAIN activity status, optional AI status and PLAYBACK diagnostics; MAIN/PLAYBACK use one shared decorative status-line UI object with no reserved slot; visibility never changes interactive geometry                                                                        |
| `behavior.statusLineTextSp`                                 | Fixed `20`, regular monospace                                                                                                                                                                                                                                                                                                                             |
| `ai.heatmapValenceCells`, `ai.heatmapArousalCells`          | Independently integer `1..128`, default `32`; no variants                                                                                                                                                                                                                                                                                                 |
| `ai.heatmapColors`                                          | Complete day/night objects with exactly topRight/bottomRight/bottomLeft/topLeft; exact defaults in AI_SCREEN contract                                                                                                                                                                                                                                     |
| `ai.heatmapAlphaMap`                                        | Seven two-digit hexadecimal strings in empty, log2 `0..3`, `4`, `5`, `6`, `7`, `8+` order; default `["3F","5F","7F","9F","BF","DF","FF"]`; no variants                                                                                                                                                                                                    |
| `ai.selectionModel`                                         | One exact object containing finite positive `temperature` default `1.0`, finite `axisMinimumSpan` in `(0,2]` default `0.10`, integer `pteMinimumGroupSize` in `1..10000` default `42`, finite `directoryDecay` and `historyDecay` in `(0,1)` default `0.5`, and exact `weights` below; recursive object deep merge applies and no Settings surface exists |
| `ai.selectionModel.weights`                                 | One exact object containing every feature weight: Arousal, Valence, PTE, Track Like/Dislike and history default `1.0`; Directory, Album and Artist Like/Dislike default `0.5`; Genre Like/Dislike default `0.25`; every value finite in `[0,100]`                                                                                                         |

`behavior.systemBrightnessWorkaroundEnabled` is a scalar Boolean, default `false`, with no variants.
It enables only the scoped optional controller in
[Inactivity and Dim Backlight](/Riff/LATEST/player-inactivity#optional-system-brightness-workaround).
`behavior.dimBacklightPercent` is a finite numeric leaf in `[0,100]`, permitting scalar or day/night
forms only, with packaged `{ "day": 30, "night": 30 }`. It controls idle dimming as a percentage of
the independent system setting in both normal-window and optional-system paths. It has no width
variants. Zero is valid. A scalar applies to both themes; a partial theme branch inherits the other.
A partial HU user file sets this leaf to day 15 / night 1 alongside the independent enabling
Boolean; all other packaged values inherit. The current user filename is `ui_config.json`; no
compatibility reader or migration is introduced.

These keys expose no Settings editor, profile override, storage column, Binder setting, REST or CLI
mutation. TTL is not a Player preference. Recent-history/transport/heatmap values do not become
Indexer state. `Arousal` is the exact spelling. Genre exclusion matches retained evidence ID or
canonical visible GNRE ID and removes matching evidence from all visible Genre rows, selection
sources, reactions, accessibility and album projection, while preserving MDRG/GNRE and other
features.

## FINAL status default

The complete packaged default skin MUST set `behavior.enableStatusLines` to `true` in every build,
including FINAL. Release channel does not select another default document or gate status consumers.
An explicit `false` in the ordinary user `ui_config.json` disables the status through the existing
merge and immutable startup snapshot; explicit `true` enables it. No Settings control or runtime
reload is introduced. The passive status rendering, safe position and lack of input/accessibility
content remain governed by the owning screen contracts.
