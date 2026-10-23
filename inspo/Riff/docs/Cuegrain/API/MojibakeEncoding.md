# MojibakeEncoding

Package: `link.roadio.cuegrain.repair`. [All classes](../API.md) · [Method index](methods.md)

Small encoding classification used by the conservative repair algorithm, separate from the general
EncodingDetection charset string.

## Declaration

```kotlin
enum class MojibakeEncoding {
    UTF8,
    WINDOWS_1251,
    OTHER,
}
```

## Member details

### Values

`UTF8`, `WINDOWS_1251`, `OTHER`. OTHER means the observation does not support one of the repair
algorithm's required hypotheses. It is not a request to try every charset.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val encoding = link.roadio.cuegrain.repair.MojibakeEncoding.OTHER
check(encoding != link.roadio.cuegrain.repair.MojibakeEncoding.UTF8)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/repair/MojibakeEncodingDetector.kt`. This
reference describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [EncodingDetection](EncodingDetection.md).
