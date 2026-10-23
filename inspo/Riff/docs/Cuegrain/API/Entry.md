# Entry

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Track or chapter within one Source. Does not manufacture a playable media item, a global timeline or
an album.

## Declaration

```kotlin
data class Entry(
    val id: String,
    val number: Int? = null,
    val kind: String = "chapter",
    val start: Position? = null,
    val end: Position? = null,
    val endOrigin: String? = null,
    val tags: List<Tag> = emptyList(),
    val indexes: List<CueIndex> = emptyList(),
    val pregap: MediaTime? = null,
    val postgap: MediaTime? = null,
    val attributes: Map<String, String> = emptyMap(),
    val artwork: List<Artwork> = emptyList(),
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter    | Meaning                                                         |
| ------------ | --------------------------------------------------------------- |
| `id`         | Entry-local identifier.                                         |
| `number`     | Optional declared track number; null is unknown.                |
| `kind`       | chapter by default; cue audio/data track kinds remain explicit. |
| `start`      | Optional file-local start Position.                             |
| `end`        | Optional file-local end; null means not known.                  |
| `endOrigin`  | Optional derivation label for the end.                          |
| `tags`       | Ordered fields for this entry; parent tags remain separate.     |
| `indexes`    | Individual CUE indexes, including file reference and time.      |
| `pregap`     | Optional synthetic pregap duration; distinct from INDEX 00.     |
| `postgap`    | Optional synthetic postgap duration.                            |
| `attributes` | Format-specific string observations.                            |
| `artwork`    | Embedded chapter artwork descriptors.                           |

## Member details

`riffFeatures: RiffFeatures` projects entry-local RIFF analysis fields. Entry has no `metadata` or
`gains` property: use Metadata.normalized and Metadata.gains explicitly. For a CUE entry, use
`Metadata.normalized(entry.tags, cue = true)`; do not apply album scope.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val entry = Entry(
    id = "track-1",
    number = 1,
    start = Position("file-1", MediaTime(0, 75)),
    tags = listOf(Tag("TITLE", "Opening"), Tag("PERFORMER", "Artist")),
)
check(Metadata.normalized(entry.tags, cue = true)["artist"] == listOf("Artist"))
check(entry.end == null)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Artwork](Artwork.md), [CueIndex](CueIndex.md), [Cuegrain](Cuegrain.md),
[MediaTime](MediaTime.md), [Metadata](Metadata.md), [Position](Position.md),
[RiffFeatures](RiffFeatures.md), [Source](Source.md), [Tag](Tag.md).
