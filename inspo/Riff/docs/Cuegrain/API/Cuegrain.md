# Cuegrain

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Entry point for bounded metadata reading. Returns independent source observations without selecting
a winner, opening paths or applying playback policy.

## Declaration

```kotlin
class Cuegrain(
    val options: ParseOptions = ParseOptions(),
    val readers: List<FormatReader> = listOf(CueReader(), Id3Reader(), FlacReader(), OggReader(), Mp4Reader()),
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter | Meaning                                                                                                                     |
| --------- | --------------------------------------------------------------------------------------------------------------------------- |
| `options` | Parsing/encoding/repair options shared by this reader; default ParseOptions().                                              |
| `readers` | Ordered format readers; default CueReader, Id3Reader, FlacReader, OggReader, Mp4Reader. Supplying a list replaces defaults. |

## Member details

### `read(input: MediaInput, hint: String? = null): Report`

Reads up to 32 header bytes, selects the first matching configured reader and returns a report. A
hint is an extension without the dot; supply `"cue"` for standalone textual CUE. The default order
is CueReader, Id3Reader, FlacReader, OggReader, Mp4Reader. Providing `readers` replaces that list.

Common input/parser failures become diagnostics and an `error` or `partial` status. Cancellation
propagates. The call is synchronous, never closes inputs and does not fetch artwork URLs.

### `readGroup(inputs: List<Pair<MediaInput, String?>>): List<Report>`

Reads the supplied inputs sequentially in their input order. Each input gets its own report and
budget. This is neither concatenation nor concurrent execution.

### `readWithReferences(input: MediaInput, hint: String? = null, resolver: FileResolver): List<Report>`

Reads the primary input, then asks the resolver about unresolved files in primary `cue` sources.
Returns the updated primary report first, followed by related reports in first-seen input-ID order.
Identical related IDs are parsed once; a resolved primary ID is not parsed again. Only
`FileReference.resolvedInput` is bound: entry positions retain reference IDs. References in related
reports are not followed recursively.

A null resolution leaves the reference unresolved without inventing an error. Resolver exceptions
propagate. The caller must close every returned handle, including duplicate-ID handles; prefer
caching handles in the resolver.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val cue = """TITLE "Demo"
FILE "demo.flac" WAVE
  TRACK 01 AUDIO
    TITLE "Opening"
    INDEX 01 00:00:00
"""
val input = ByteArrayInput(cue.toByteArray(), "demo.cue")
val reader = Cuegrain()
val report = reader.read(input, "cue")
check(report.status == "ok")
check(report.sources.single().metadata["album"] == listOf("Demo"))
check(reader.readGroup(listOf(input to "cue")).size == 1)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Cuegrain.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [CueReader](CueReader.md), [Entry](Entry.md), [FileReference](FileReference.md),
[FileResolver](FileResolver.md), [FlacReader](FlacReader.md), [FormatReader](FormatReader.md),
[Id3Reader](Id3Reader.md), [MediaInput](MediaInput.md), [Mp4Reader](Mp4Reader.md),
[OggReader](OggReader.md), [ParseOptions](ParseOptions.md), [Report](Report.md).
