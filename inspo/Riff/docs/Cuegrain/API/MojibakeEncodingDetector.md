# MojibakeEncodingDetector

Package: `link.roadio.cuegrain.repair`. [All classes](../API.md) · [Method index](methods.md)

Detector contract for the grouped-text repair API; deliberately independent of an Android or native
backend.

## Declaration

```kotlin
interface MojibakeEncodingDetector
```

## Member details

### Members

`revision: String` identifies the supplied detector implementation to consumers that version
metadata dependencies. MojibakeRepair does not persist/cache that revision.

`detect(input: ByteArray): MojibakeEncodingDetection` supplies reliable bounded evidence. The
package constant `CED_MAX_INPUT_BYTES: Int = 65_536` owns the sampling cap. Adapt an
EncodingDetector's charset result explicitly; the interfaces are not interchangeable.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val detector = object : link.roadio.cuegrain.repair.MojibakeEncodingDetector {
    override val revision = "no-guess-example"
    override fun detect(input: ByteArray) = link.roadio.cuegrain.repair.MojibakeEncodingDetection(
        link.roadio.cuegrain.repair.MojibakeEncoding.OTHER, false, input.size,
    )
}
check(detector.revision == "no-guess-example")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/repair/MojibakeEncodingDetector.kt`. This
reference describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [EncodingDetector](EncodingDetector.md),
[MojibakeEncodingDetection](MojibakeEncodingDetection.md), [MojibakeRepair](MojibakeRepair.md).
