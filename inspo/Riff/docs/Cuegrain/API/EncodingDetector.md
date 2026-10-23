# EncodingDetector

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Pluggable byte-encoding detector used only after deterministic CUE decoding and optionally by
conservative text repair.

## Declaration

```kotlin
interface EncodingDetector
```

## Member details

### `detect(bytes: ByteArray): EncodingDetection`

Return charset, reliability and consumed-byte count. Core has no automatic service lookup: pass the
instance through ParseOptions.detector. Standalone CUE tries BOM, explicit charset and strict UTF-8
before sampling at most 65,536 bytes for detection.

Do not label unverified guesses reliable. Exceptions from a custom detector are subject to
Cuegrain's parse exception handling when raised during reading; the interface itself is synchronous.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val detector = object : EncodingDetector {
    override fun detect(bytes: ByteArray) =
        EncodingDetection(null, isReliable = false, bytesConsumed = bytes.size)
}
val options = ParseOptions(detector = detector)
check(options.detector === detector)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [EncodingDetection](EncodingDetection.md),
[ParseOptions](ParseOptions.md).
