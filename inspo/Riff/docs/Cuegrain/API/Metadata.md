# Metadata

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Stateless shared projections for decoded tag lists. Use this object directly; it has no constructor.

## Declaration

```kotlin
object Metadata
```

## Member details

### `normalized(tags: List<Tag>, albumScope: Boolean = false, cue: Boolean = albumScope): Map<String, List<String>>`

Maps recognized aliases to common field names while retaining repeated value order. Unknown raw tags
remain in the input but have no normalized key. It does not split list-like strings, normalize
application identity or merge sources.

For CUE, `cue = true` maps PERFORMER to artist. With albumScope, title maps to album and artist to
albumArtist. MP4's lowercase raw `gnre` is one-based and normalized to a zero-based numeric string;
malformed/out-of-range values are omitted.

### `gains(tags: List<Tag>, ctx: ParseContext? = null): List<Gain>`

Decodes ReplayGain, R128, reference loudness and Sound Check tags. Invalid values are omitted;
passing a reader's ParseContext records diagnostics. Without context there is no diagnostic side
channel. LAME and Opus-header observations come from their container readers, not this text-only
method.

### `riffFeatures(tags: List<Tag>): RiffFeatures`

Projects finite RIFF analysis fields without removing originals. Direct calls return values only;
Cuegrain.read also diagnoses invalid/duplicate/orphan fields. This method does not run a classifier.

### Normalized field names

Recognized output keys are `title`, `album`, `artist`, `performer`, `albumArtist`, `trackNumber`,
`trackTotal`, `discNumber`, `discTotal`, `genre`, `date`, `composer`, `isrc`, `mediaKind` and
`comment`. MP4 `stik` remains a raw numeric string in `mediaKind`; consumers interpret supported
values. Values remain strings: a track-number text such as `3/12` is not converted to an integer or
split into totals. CUE scope remapping is described above.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val tags = listOf(Tag("TITLE", "Album"), Tag("PERFORMER", "Artist"), Tag("GENRE", "Rock"), Tag("GENRE", "Live"))
val album = Metadata.normalized(tags, albumScope = true)
check(album["albumArtist"] == listOf("Artist"))
check(album["genre"] == listOf("Rock", "Live"))
check(Metadata.gains(listOf(Tag("REPLAYGAIN_TRACK_GAIN", "-4 dB"))).single().value == -4.0)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Text.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Gain](Gain.md), [ParseContext](ParseContext.md),
[RiffFeatures](RiffFeatures.md), [Tag](Tag.md).
