# Id3Reader

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

MP3 ID3v2.2/2.3/2.4 and ID3v1 fields, CHAP/CTOC, embedded CUE and bounded LAME observations.

## Declaration

```kotlin
class Id3Reader : FormatReader
```

## Member details

### `Id3Reader()`

No-argument constructor. Implements [FormatReader](FormatReader.md), with `name = "id3"`.

### `matches(header: ByteArray, hint: String?): Boolean`

Matches an ID3 prefix, mp3 hint or compatible MPEG audio header. Compressed/encrypted ID3 frames are
diagnosed instead of decoded. Matching is routing, not validation.

### `read(context: ParseContext): List<Source>`

Parses through the supplied bounded context and returns independent representations. Prefer
Cuegrain.read for ordinary use: it owns error-to-diagnostic conversion and final feature
validation/optional repair. Direct calls inside an extension share its context and can throw parser
failures.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reader = Id3Reader()
check(reader.matches("ID3".toByteArray(), null))
val emptyTag = byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, 0)
val result = Cuegrain(readers = listOf(reader)).read(ByteArrayInput(emptyTag), "mp3")
check(result.sources.any { it.kind == "id3" && it.attributes["version"] == "2.4" })
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Id3Reader.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [FormatReader](FormatReader.md),
[ParseContext](ParseContext.md), [Source](Source.md).
