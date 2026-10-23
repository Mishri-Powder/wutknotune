# OggReader

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Ogg Vorbis/Opus comment metadata and independent Opus header gain.

## Declaration

```kotlin
class OggReader : FormatReader
```

## Member details

### `OggReader()`

No-argument constructor. Implements [FormatReader](FormatReader.md), with `name = "ogg"`.

### `matches(header: ByteArray, hint: String?): Boolean`

Requires the OggS prefix. Page walking skips audio decoding; CRC and complete audio-stream
validation are outside scope. Matching is routing, not validation.

### `read(context: ParseContext): List<Source>`

Parses through the supplied bounded context and returns independent representations. Prefer
Cuegrain.read for ordinary use: it owns error-to-diagnostic conversion and final feature
validation/optional repair. Direct calls inside an extension share its context and can throw parser
failures.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reader = OggReader()
check(reader.name == "ogg")
check(reader.matches("OggS".toByteArray(), null))
check(!reader.matches(byteArrayOf(), "opus"))
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/OggReader.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [FormatReader](FormatReader.md),
[ParseContext](ParseContext.md), [Source](Source.md).
