# Limits

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Positive per-input safety budgets. These bound parser work and reads; they do not establish that
metadata is semantically valid.

## Declaration

```kotlin
data class Limits(
    val metadataBytes: Int = 32 * 1024 * 1024,
    val totalReadBytes: Long = 128L * 1024 * 1024,
    val entries: Int = 10000,
    val fields: Int = 65536,
    val textBytes: Int = 1024 * 1024,
    val nesting: Int = 16,
    val diagnostics: Int = 1000,
    val elements: Int = 100000,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter        | Meaning                                                                                                |
| ---------------- | ------------------------------------------------------------------------------------------------------ |
| `metadataBytes`  | Maximum size of one bounded metadata read; default 32 MiB. Also used by readers to bound structures.   |
| `totalReadBytes` | Aggregate requested bytes; default 128 MiB. Re-reading bytes consumes budget again.                    |
| `entries`        | Reader-enforced entry count; default 10,000.                                                           |
| `fields`         | Default boundedCount ceiling; default 65,536.                                                          |
| `textBytes`      | Reader-enforced text bound; default 1 MiB. Some CUE checks use character length.                       |
| `nesting`        | Reader-enforced structure depth; default 16.                                                           |
| `diagnostics`    | Diagnostic count cap; default 1,000. Further diagnostics replace the last slot with LIMIT_DIAGNOSTICS. |
| `elements`       | ParseContext checkpoint budget; default 100,000. Checks and reads consume checkpoints.                 |

## Member details

Construction rejects zero or negative values with IllegalArgumentException. Counts are checked at
reader-specific boundaries; do not interpret a limit as a global validator for arbitrary hand-built
model objects.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val limits = Limits(metadataBytes = 4096, totalReadBytes = 16384, entries = 50)
check(limits.metadataBytes == 4096)
check(runCatching { Limits(entries = 0) }.isFailure)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Diagnostic](Diagnostic.md),
[ParseContext](ParseContext.md).
