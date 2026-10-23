# MojibakeRepair

Package: `link.roadio.cuegrain.repair`. [All classes](../API.md) · [Method index](methods.md)

Stateless object that makes one conservative decision for an ordered group of related text fields.

## Declaration

```kotlin
object MojibakeRepair
```

## Member details

### `repairTrack(values: List<String>, detector: MojibakeEncodingDetector): List<MojibakeRepairResult>`

Returns one result per input in the same order. An empty input returns an empty list. Reversible
candidates require reliable bounded detector evidence and a unique dominating candidate set.
Ambiguity or any replacement-character field preserves the complete input group.

This method does not split lists, merge metadata, normalize identity, mutate files or infer missing
tags. Supply complete related strings in a stable order and repair once. It has no ParseContext and
therefore no parsing cancellation callback. The package constant CED_MAX_INPUT_BYTES bounds detector
samples, not the caller's entire list.

For automatic Source-level repair, use ParseOptions.repairMojibake with EncodingDetector. For
consumer-defined grouping, adapt the detector and call this API directly.

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
val repaired = link.roadio.cuegrain.repair.MojibakeRepair.repairTrack(listOf("Title", "Artist"), detector)
check(repaired.map { it.value } == listOf("Title", "Artist"))
check(repaired.none { it.repaired })
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/repair/MojibakeRepair.kt`. This reference
describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [EncodingDetector](EncodingDetector.md),
[MojibakeEncodingDetector](MojibakeEncodingDetector.md),
[MojibakeRepairResult](MojibakeRepairResult.md), [ParseContext](ParseContext.md),
[ParseOptions](ParseOptions.md), [Source](Source.md).
