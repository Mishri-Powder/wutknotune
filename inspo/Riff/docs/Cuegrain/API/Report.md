# Report

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Top-level result for one input. Constructed by Cuegrain.read; data does not close or own its input.

## Declaration

```kotlin
data class Report(
    val input: String,
    val sources: List<Source>,
    val diagnostics: List<Diagnostic>,
    val status: String,
    val schemaVersion: Int = 1,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter       | Meaning                                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------------------- |
| `input`         | MediaInput.id for the parsed input.                                                                                 |
| `sources`       | Independent source representations; may be empty.                                                                   |
| `diagnostics`   | Bounded ordered diagnostics, including error severity.                                                              |
| `status`        | ok if no error diagnostics; partial if errors coexist with returned sources; error if errors exist without sources. |
| `schemaVersion` | JSON model version, currently 1. The constructor default is 1.                                                      |

## Member details

Status is a String, not an enum. `ok` does not imply playable audio or nonempty metadata. Manually
constructing a Report does not validate consistency between its status and diagnostics.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val report = Cuegrain().read(ByteArrayInput(byteArrayOf(1, 2), "unknown"))
check(report.status == "error")
check(report.diagnostics.any { it.code == "FORMAT_UNSUPPORTED" })
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Diagnostic](Diagnostic.md), [MediaInput](MediaInput.md),
[Source](Source.md).
