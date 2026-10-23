# CueReader

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Standalone/embedded textual CUE with file references, tracks, indexes, gaps and scoped tags.

## Declaration

```kotlin
class CueReader : FormatReader
```

## Member details

### `CueReader()`

No-argument constructor. Implements [FormatReader](FormatReader.md), with `name = "cue"`.

### `matches(header: ByteArray, hint: String?): Boolean`

Matches only a case-insensitive cue extension hint. Header text alone does not select this reader.
Matching is routing, not validation.

### `read(context: ParseContext): List<Source>`

Parses through the supplied bounded context and returns independent representations. Prefer
Cuegrain.read for ordinary use: it owns error-to-diagnostic conversion and final feature
validation/optional repair. Direct calls inside an extension share its context and can throw parser
failures.

### `parse(text: String, ctx: ParseContext, sourceId: String, embeddedFile: String? = null): Source`

Parses already-decoded CUE text for an extension/embedding reader. The callback-supplied ctx owns
budgets/diagnostics, sourceId identifies this sheet, and embeddedFile supplies an input only when
FILE is omitted. Explicit FILE names remain unresolved. Unlike read, this method does not perform
byte decoding or attach charset attributes.

See [embedded CUE example](https://wiki.271.pics/Cuegrain/examples/extensions).

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reader = CueReader()
check(reader.matches(byteArrayOf(), "CUE"))
val result = Cuegrain(readers = listOf(reader)).read(
    ByteArrayInput("FILE \"disc.flac\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\n".toByteArray()), "cue",
)
check(result.sources.single().entries.single().indexes.single().number == 1)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/CueReader.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [FormatReader](FormatReader.md),
[ParseContext](ParseContext.md), [Source](Source.md).
