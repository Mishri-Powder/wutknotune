# CedEncodingDetector

Package: `link.roadio.cuegrain.ced`. [All classes](../API.md) · [Method index](methods.md)

Optional native Compact Encoding Detection backend implementing EncodingDetector. Located in the
separate CED artifact; absent from default core/CLI builds.

## Declaration

```kotlin
class CedEncodingDetector : EncodingDetector
```

## Member details

### `CedEncodingDetector()`

Initializes the native backend. Desktop extracts its packaged host library to a temporary file;
Android uses System.loadLibrary. Missing/incompatible native packaging can fail construction/class
initialization. No automatic all-platform native artifact is implied.

### `detect(bytes: ByteArray): EncodingDetection`

Requires at most 65,536 bytes; larger samples throw IllegalArgumentException. Returns a supported
charset name or null, native reliability and bytes consumed. It does not decode text itself.
Defaults use QUERY_CORPUS and no language/URL/encoding hints.

See [build and native packaging](../build-and-cli.md).

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val detector = link.roadio.cuegrain.ced.CedEncodingDetector()
val sample = "A simple UTF-8 metadata sample".toByteArray()
val detection = detector.detect(sample)
check(detection.bytesConsumed in 0..sample.size)
val reader = Cuegrain(ParseOptions(detector = detector))
check(reader.options.detector === detector)
```

## Source and related API

Implementation: `ced/src/main/kotlin/link/roadio/cuegrain/ced/CedEncodingDetector.kt`. This
reference describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [EncodingDetection](EncodingDetection.md),
[EncodingDetector](EncodingDetector.md).
