# Source

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

One independent container, CUE sheet or chapter representation. Sources coexist; Cuegrain does not
choose an application winner.

## Declaration

```kotlin
data class Source(
    val kind: String,
    val id: String,
    val location: String,
    val tags: List<Tag> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val tables: List<TableOfContents> = emptyList(),
    val files: List<FileReference> = emptyList(),
    val artwork: List<Artwork> = emptyList(),
    val gains: List<Gain> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter    | Meaning                                                                                                                       |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| `kind`       | Representation name, such as cue, id3, vorbis, mp4.quicktime or mp4.nero; inspect actual source.kind rather than reader.name. |
| `id`         | Source-local identifier used for provenance.                                                                                  |
| `location`   | Origin/containing input location; can differ from source ID.                                                                  |
| `tags`       | Ordered raw tags, including repeated and unknown fields.                                                                      |
| `entries`    | Ordered chapter or track entries.                                                                                             |
| `tables`     | Separate TOC graph descriptors.                                                                                               |
| `files`      | Declared CUE file references and explicit resolutions.                                                                        |
| `artwork`    | Picture descriptors; bytes are opt-in.                                                                                        |
| `gains`      | Decoded gain observations from this source only.                                                                              |
| `attributes` | Format-specific string observations; not a universal schema.                                                                  |

## Member details

### Computed properties

`metadata: Map<String, List<String>>` delegates to Metadata.normalized; kind `cue` uses album scope.
It does not mutate raw tags.

`riffFeatures: RiffFeatures` delegates to Metadata.riffFeatures. Neither property merges other
sources. Data-class construction itself does not validate source consistency.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val source = Source("cue", "sheet", "album.cue", tags = listOf(Tag("TITLE", "Album")))
check(source.metadata["album"] == listOf("Album"))
check(source.tags.single().key == "TITLE")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Artwork](Artwork.md), [Cuegrain](Cuegrain.md), [Entry](Entry.md),
[FileReference](FileReference.md), [Gain](Gain.md), [Metadata](Metadata.md),
[RiffFeatures](RiffFeatures.md), [TableOfContents](TableOfContents.md), [Tag](Tag.md).
