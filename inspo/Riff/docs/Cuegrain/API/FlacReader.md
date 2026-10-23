# FlacReader

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

FLAC metadata blocks including STREAMINFO timing, native CUESHEET, Vorbis comments and artwork.

## Declaration

```kotlin
class FlacReader : FormatReader
```

## Member details

### `FlacReader()`

No-argument constructor. Implements [FormatReader](FormatReader.md), with `name = "flac"`.

### `matches(header: ByteArray, hint: String?): Boolean`

Requires the fLaC magic prefix; a flac hint alone does not select it. Native CUESHEET and textual
CUE remain independent sources. Matching is routing, not validation.

### `read(context: ParseContext): List<Source>`

Parses through the supplied bounded context and returns independent representations. Prefer
Cuegrain.read for ordinary use: it owns error-to-diagnostic conversion and final feature
validation/optional repair. Direct calls inside an extension share its context and can throw parser
failures.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reader = FlacReader()
check(reader.name == "flac")
check(reader.matches("fLaC".toByteArray(), null))
check(!reader.matches(byteArrayOf(), "flac"))
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/FlacReader.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [FormatReader](FormatReader.md),
[ParseContext](ParseContext.md), [Source](Source.md).
