# Mp4Reader

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

ISO BMFF iTunes-style metadata plus independent QuickTime and Nero chapters.

## Declaration

```kotlin
class Mp4Reader : FormatReader
```

## Member details

### `Mp4Reader()`

No-argument constructor. Implements [FormatReader](FormatReader.md), with `name = "mp4"`.

### `matches(header: ByteArray, hint: String?): Boolean`

Requires at least eight prefix bytes and a first atom of type ftyp, moov, free, wide or mdat.
Ordinary ilst is supported, not arbitrary mdta key tables; fragmented chapter layouts and
unsupported edits are diagnosed. Matching is routing, not validation.

### `read(context: ParseContext): List<Source>`

Parses through the supplied bounded context and returns independent representations. Prefer
Cuegrain.read for ordinary use: it owns error-to-diagnostic conversion and final feature
validation/optional repair. Direct calls inside an extension share its context and can throw parser
failures.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reader = Mp4Reader()
val header = byteArrayOf(0, 0, 0, 8) + "ftyp".toByteArray()
check(reader.matches(header, null))
check(reader.name == "mp4")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Mp4Reader.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [FormatReader](FormatReader.md),
[ParseContext](ParseContext.md), [Source](Source.md).
